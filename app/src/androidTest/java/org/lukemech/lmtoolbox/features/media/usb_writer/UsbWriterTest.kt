package org.lukemech.lmtoolbox.features.media.usb_writer

import org.lukemech.lmtoolbox.e2e.ToolTest

/**
 * Bootable USB, opened from the home page and gone through (see ToolTest): the sample picture picked as an image
 * (written as it is, told it has no partition table), then the drive step, with no drive on the emulator.
 */
class UsbWriterTest : ToolTest(UsbWriterFeature)
