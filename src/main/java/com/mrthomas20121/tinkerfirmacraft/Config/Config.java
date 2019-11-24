package com.mrthomas20121.tinkerfirmacraft.Config;

import com.mrthomas20121.tinkerfirmacraft.TinkerFirmaCraft;
import com.mrthomas20121.tinkerfirmacraft.proxy.CommonProxy;
import net.minecraftforge.common.config.Configuration;
import org.apache.logging.log4j.Level;

public class Config {

    private static final String CATEGORY_METERIALS = "materials";

    // This values below you can access elsewhere in your mod:
    public static boolean black_steel = true;
    public static boolean red_steel = true;
    public static boolean blue_steel = true;

    // Call this from CommonProxy.preInit(). It will create our config if it doesn't
    // exist yet and read the values if it does exist.
    public static void readConfig() {
        Configuration cfg = CommonProxy.config;
        try {
            cfg.load();
            initMaterialConfig(cfg);
        } catch (Exception e1) {
            TinkerFirmaCraft.logger.log(Level.ERROR, "Problem loading config file!", e1);
        } finally {
            if (cfg.hasChanged()) {
                cfg.save();
            }
        }
    }

    private static void initMaterialConfig(Configuration cfg) {
        cfg.addCustomCategoryComment(CATEGORY_METERIALS, "Tinker Materials");
        // cfg.getBoolean() will get the value in the config if it is already specified there. If not it will create the value.
        black_steel = cfg.getBoolean("black_steel", CATEGORY_METERIALS, black_steel, "Set to false if you don't want black_steel material to load");
        red_steel = cfg.getBoolean("red_steel", CATEGORY_METERIALS, red_steel, "Set to false if you don't want red_steel material to load");
        // yourRealName = cfg.getString("realName", CATEGORY_GENERAL, yourRealName, "Set your real name here");
    }
}