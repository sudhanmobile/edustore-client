/*
 * Copyright (C) 2018 Senecto Limited
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301, USA.
 */

package org.edustore.app.nearby;

import android.content.Context;
import android.net.Uri;

/**
 * Dummy version for basic app flavor.
 */
public class SwapWorkflowActivity {

    public static final String EXTRA_PREVENT_FURTHER_SWAP_REQUESTS = "preventFurtherSwap";

    public static void requestSwap(Context context, Uri uri) {
    }
}
