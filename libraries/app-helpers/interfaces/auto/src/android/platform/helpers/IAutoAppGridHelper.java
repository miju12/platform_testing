/*
 * Copyright (C) 2019 The Android Open Source Project
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

package android.platform.helpers;

public interface IAutoAppGridHelper extends IAppHelper, Scrollable {
    /**
     * Setup expectations: In App grid.
     *
     * Check if device is currently at the top of app grid.
     */
    public boolean isTop();

    /**
     * Setup expectations: In App grid.
     *
     * Check if device is currently at the bottom of app grid.
     */
    public boolean isBottom();

    /**
     * Setup expectations: In App grid.
     *
     * <p>Scroll up on page.
     */
    boolean scrollUpOnePage();

    /**
     * Setup expectations: In App grid.
     *
     * <p>Scroll down on page.
     */
    boolean scrollDownOnePage();

    /**
     * Setup expectations: In App grid.
     *
     * <p>Find and open an application.
     */
    void openApp(String appName);
}
