/*
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.server.wm.traces.common.layers

import com.android.server.wm.traces.common.ITraceEntry
import com.android.server.wm.traces.common.prettyTimestamp

/**
 * Represents a single Layer trace entry.
 *
 * This is a generic object that is reused by both Flicker and Winscope and cannot
 * access internal Java/Android functionality
 *
 **/
open class LayerTraceEntry constructor(
    override val timestamp: Long, // hierarchical representation of layers
    val rootLayers: List<Layer>
) : ITraceEntry {
    private val formattedTimestamp by lazy { prettyTimestamp(timestamp) }
    private val _opaqueLayers = mutableListOf<Layer>()
    private val _transparentLayers = mutableListOf<Layer>()

    val flattenedLayers: Array<Layer> by lazy {
        val layers = mutableListOf<Layer>()
        val roots = rootLayers.fillOcclusionState().toMutableList()
        while (roots.isNotEmpty()) {
            val layer = roots.removeAt(0)
            layers.add(layer)
            roots.addAll(layer.children)
        }
        layers.toTypedArray()
    }

    private fun List<Layer>.topDownTraversal(): List<Layer> {
        return this
                .sortedBy { it.z }
                .flatMap { it.topDownTraversal() }
    }

    val visibleLayers: Array<Layer>
        get() = flattenedLayers.filter { it.isVisible && !it.isHiddenByParent }.toTypedArray()

    val opaqueLayers: Array<Layer> get() = _opaqueLayers.toTypedArray()

    val transparentLayers: Array<Layer> get() = _transparentLayers.toTypedArray()

    private fun Layer.topDownTraversal(): List<Layer> {
        val traverseList = mutableListOf(this)

        this.children.sortedBy { it.z }
                .forEach { childLayer ->
                    traverseList.addAll(childLayer.topDownTraversal())
                }

        return traverseList
    }

    private fun List<Layer>.fillOcclusionState(): List<Layer> {
        val traversalList = topDownTraversal().reversed()

        traversalList.forEach { layer ->
            val visible = layer.isVisible

            if (visible) {
                layer.occludedBy.addAll(_opaqueLayers
                    .filter { it.contains(layer) && !it.hasRoundedCorners })
                layer.partiallyOccludedBy.addAll(
                    _opaqueLayers.filter { it.overlaps(layer) && it !in layer.occludedBy })
                layer.coveredBy.addAll(_transparentLayers.filter { it.overlaps(layer) })

                if (layer.isOpaque) {
                    _opaqueLayers.add(layer)
                } else {
                    _transparentLayers.add(layer)
                }
            }
        }

        return this
    }

    fun getLayerWithBuffer(name: String): Layer? {
        return flattenedLayers.firstOrNull {
            it.name.contains(name) && it.activeBuffer != null
        }
    }

    /**
     * Check if at least one window which matches provided window name is visible.
     */
    fun isVisible(windowName: String): Boolean =
        visibleLayers.any { it.name == windowName }

    override fun toString(): String {
        return this.formattedTimestamp
    }

    companion object {
        /** Constructs the layer hierarchy from a flattened list of layers.  */
        fun fromFlattenedLayers(
            timestamp: Long,
            layers: Array<Layer>,
            orphanLayerCallback: ((Layer) -> Boolean)?
        ): LayerTraceEntry {
            val layerMap: MutableMap<Int, Layer> = HashMap()
            val orphans = mutableListOf<Layer>()

            for (layer in layers) {
                val id = layer.id

                if (layerMap.containsKey(id)) {
                    throw RuntimeException("Duplicate layer id found: $id")
                }
                layerMap[id] = layer
            }

            for (layer in layers) {
                val parentId = layer.parentId

                val parentLayer = layerMap[parentId]
                if (parentLayer == null) {
                    orphans.add(layer)
                    continue
                }
                parentLayer.addChild(layer)
                layer.parent = parentLayer
            }

            // Getting the first orphan works because when dumping the layers, the root layer comes
            // first, and given that orphans are added in the same order as the layers are provided
            // in the first orphan layer should be the root layer.
            val rootLayer = orphans.firstOrNull() ?: throw IllegalStateException(
                "Display root layer not found.")
            orphans.remove(rootLayer)

            // Find all root layers (any sibling of the root layer is considered a root layer in the trace)
            val rootLayers = mutableListOf(rootLayer)
            for (orphan in orphans) {
                if (orphan.parentId == rootLayer.parentId) {
                    rootLayers.add(orphan)
                }
            }

            // Remove RootLayers from orphans
            orphans.removeAll(rootLayers)

            // Fail if we find orphan layers.
            orphans.forEach { orphan ->
                // Workaround for b/141326137, ignore the existence of an orphan layer
                if (orphanLayerCallback == null || orphanLayerCallback.invoke(orphan)) {
                    return@forEach
                }
                val orphanId: Int = orphan.parentId
                throw RuntimeException(
                        ("Failed to parse layers trace. Found orphan layer with id = ${orphan.id}" +
                                " with parentId = ${orphan.parentId}"))
            }

            return LayerTraceEntry(timestamp, rootLayers)
        }
    }
}