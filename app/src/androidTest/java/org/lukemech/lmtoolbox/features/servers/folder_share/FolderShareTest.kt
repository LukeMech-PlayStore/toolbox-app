package org.lukemech.lmtoolbox.features.servers.folder_share

import org.lukemech.lmtoolbox.e2e.ToolTest

/**
 * Folder share, opened from the home page and gone through (see ToolTest): no folder picked (the picker's backed
 * out of), so nothing's shared; its options and tunnels' switches tried. Its HTTP has its own test (FolderShareHttpTest).
 */
class FolderShareTest : ToolTest(FolderShareFeature)
