package org.lukemech.lmtoolbox.helpers

import org.lukemech.lmtoolbox.features.electronics.adc.AdcFeature
import org.lukemech.lmtoolbox.features.electronics.battery_life.BatteryLifeFeature
import org.lukemech.lmtoolbox.features.electronics.bjt.BjtFeature
import org.lukemech.lmtoolbox.features.electronics.capacitor_charge.CapacitorChargeFeature
import org.lukemech.lmtoolbox.features.electronics.coil.CoilFeature
import org.lukemech.lmtoolbox.features.electronics.color_code.ColorCodeFeature
import org.lukemech.lmtoolbox.features.electronics.decibel.DecibelFeature
import org.lukemech.lmtoolbox.features.electronics.energy.EnergyFeature
import org.lukemech.lmtoolbox.features.electronics.frequency.FrequencyFeature
import org.lukemech.lmtoolbox.features.electronics.heatsink.HeatsinkFeature
import org.lukemech.lmtoolbox.features.electronics.led_resistor.LedResistorFeature
import org.lukemech.lmtoolbox.features.electronics.ne555.Ne555Feature
import org.lukemech.lmtoolbox.features.electronics.number_base.NumberBaseFeature
import org.lukemech.lmtoolbox.features.electronics.ohm.OhmFeature
import org.lukemech.lmtoolbox.features.electronics.opamp.OpAmpFeature
import org.lukemech.lmtoolbox.features.electronics.pcb_trace.PcbTraceFeature
import org.lukemech.lmtoolbox.features.electronics.power.PowerFeature
import org.lukemech.lmtoolbox.features.electronics.pt100.Pt100Feature
import org.lukemech.lmtoolbox.features.electronics.range_converter.RangeConverterFeature
import org.lukemech.lmtoolbox.features.electronics.rc_filter.RcFilterFeature
import org.lukemech.lmtoolbox.features.electronics.reactance.ReactanceFeature
import org.lukemech.lmtoolbox.features.electronics.regulator.RegulatorFeature
import org.lukemech.lmtoolbox.features.electronics.resistor_ratio.ResistorRatioFeature
import org.lukemech.lmtoolbox.features.electronics.rms.RmsFeature
import org.lukemech.lmtoolbox.features.electronics.series_parallel.SeriesParallelFeature
import org.lukemech.lmtoolbox.features.electronics.smd_code.SmdCodeFeature
import org.lukemech.lmtoolbox.features.electronics.voltage_divider.VoltageDividerFeature
import org.lukemech.lmtoolbox.features.electronics.voltage_drop.VoltageDropFeature
import org.lukemech.lmtoolbox.features.electronics.y_delta.YDeltaFeature
import org.lukemech.lmtoolbox.features.electronics.zener.ZenerFeature
import org.lukemech.lmtoolbox.features.finance.currencyconv.CurrencyConverterFeature
import org.lukemech.lmtoolbox.features.measure.compass.CompassFeature
import org.lukemech.lmtoolbox.features.measure.light_meter.LightMeterFeature
import org.lukemech.lmtoolbox.features.measure.magnetometer.MagnetometerFeature
import org.lukemech.lmtoolbox.features.measure.ruler.RulerFeature
import org.lukemech.lmtoolbox.features.measure.sound_meter.SoundMeterFeature
import org.lukemech.lmtoolbox.features.measure.stroboscope.StroboscopeFeature
import org.lukemech.lmtoolbox.features.measure.spirit_level.SpiritLevelFeature
import org.lukemech.lmtoolbox.features.measure.vibration_meter.VibrationMeterFeature
import org.lukemech.lmtoolbox.features.media.file_hash.FileHashFeature
import org.lukemech.lmtoolbox.features.media.file_size.FileSizeFeature
import org.lukemech.lmtoolbox.features.media.image_resize.ImageResizerFeature
import org.lukemech.lmtoolbox.features.media.md_edit.MetadataEditorFeature
import org.lukemech.lmtoolbox.features.media.media_dl.MediaDownloaderFeature
import org.lukemech.lmtoolbox.features.media.qr_gen.QrGenFeature
import org.lukemech.lmtoolbox.features.media.video_enc.VideoEncoderFeature
import org.lukemech.lmtoolbox.features.reference.arduino.ArduinoFeature
import org.lukemech.lmtoolbox.features.reference.audio_jack.AudioJackFeature
import org.lukemech.lmtoolbox.features.reference.batteries.BatteriesFeature
import org.lukemech.lmtoolbox.features.reference.capacitor_code.CapacitorCodeFeature
import org.lukemech.lmtoolbox.features.reference.car_audio.CarAudioFeature
import org.lukemech.lmtoolbox.features.reference.display.DisplayFeature
import org.lukemech.lmtoolbox.features.reference.ethernet.EthernetFeature
import org.lukemech.lmtoolbox.features.reference.fuses.FusesFeature
import org.lukemech.lmtoolbox.features.reference.ip_code.IpCodeFeature
import org.lukemech.lmtoolbox.features.reference.isp_icsp.IspIcspFeature
import org.lukemech.lmtoolbox.features.reference.jtag_swd.JtagSwdFeature
import org.lukemech.lmtoolbox.features.reference.logic_gates.LogicGatesFeature
import org.lukemech.lmtoolbox.features.reference.obd2.Obd2Feature
import org.lukemech.lmtoolbox.features.reference.pc_power.PcPowerFeature
import org.lukemech.lmtoolbox.features.reference.resistivity.ResistivityFeature
import org.lukemech.lmtoolbox.features.reference.rpi_gpio.RpiGpioFeature
import org.lukemech.lmtoolbox.features.reference.esp_boards.EspBoardsFeature
import org.lukemech.lmtoolbox.features.reference.rs232.Rs232Feature
import org.lukemech.lmtoolbox.features.reference.sd_card.SdCardFeature
import org.lukemech.lmtoolbox.features.reference.si_prefixes.SiPrefixesFeature
import org.lukemech.lmtoolbox.features.reference.smd_packages.SmdPackagesFeature
import org.lukemech.lmtoolbox.features.reference.standard_values.StandardValuesFeature
import org.lukemech.lmtoolbox.features.reference.thermocouples.ThermocouplesFeature
import org.lukemech.lmtoolbox.features.reference.trailer.TrailerFeature
import org.lukemech.lmtoolbox.features.reference.usb.UsbFeature
import org.lukemech.lmtoolbox.features.reference.wire_gauge.WireGaugeFeature
import org.lukemech.lmtoolbox.features.reference.xlr_dmx.XlrDmxFeature
import org.lukemech.lmtoolbox.features.servers.folder_share.FolderShareFeature
import org.lukemech.lmtoolbox.features.servers.mc_server.MinecraftServerFeature

/** All the app's tools, in this order wherever they're listed. Add a new one here. */
object FeatureRegistry {
    val features: List<AppFeature> = listOf(
        RulerFeature,
        SpiritLevelFeature,
        CompassFeature,
        SoundMeterFeature,
        LightMeterFeature,
        MagnetometerFeature,
        VibrationMeterFeature,
        StroboscopeFeature,
        QrGenFeature,
        MetadataEditorFeature,
        CurrencyConverterFeature,
        MediaDownloaderFeature,
        VideoEncoderFeature,
        ImageResizerFeature,
        FileSizeFeature,
        FileHashFeature,
        MinecraftServerFeature,
        FolderShareFeature,
        ColorCodeFeature,
        SmdCodeFeature,
        OhmFeature,
        PowerFeature,
        ReactanceFeature,
        RcFilterFeature,
        VoltageDividerFeature,
        ResistorRatioFeature,
        SeriesParallelFeature,
        CapacitorChargeFeature,
        OpAmpFeature,
        BjtFeature,
        LedResistorFeature,
        ZenerFeature,
        RegulatorFeature,
        Ne555Feature,
        HeatsinkFeature,
        BatteryLifeFeature,
        EnergyFeature,
        CoilFeature,
        VoltageDropFeature,
        PcbTraceFeature,
        YDeltaFeature,
        Pt100Feature,
        DecibelFeature,
        FrequencyFeature,
        AdcFeature,
        RangeConverterFeature,
        NumberBaseFeature,
        RmsFeature,
        StandardValuesFeature,
        SiPrefixesFeature,
        WireGaugeFeature,
        CapacitorCodeFeature,
        IpCodeFeature,
        SmdPackagesFeature,
        RpiGpioFeature,
        ArduinoFeature,
        EspBoardsFeature,
        UsbFeature,
        EthernetFeature,
        Rs232Feature,
        JtagSwdFeature,
        IspIcspFeature,
        DisplayFeature,
        Obd2Feature,
        PcPowerFeature,
        SdCardFeature,
        XlrDmxFeature,
        AudioJackFeature,
        CarAudioFeature,
        TrailerFeature,
        LogicGatesFeature,
        ResistivityFeature,
        ThermocouplesFeature,
        FusesFeature,
        BatteriesFeature
    )

    /** The tool with [route], if there's one. */
    fun byRoute(route: String?): AppFeature? = features.firstOrNull { it.route == route }
}
