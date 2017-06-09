package de.intranda.goobi.plugins;

import java.io.File;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.SubnodeConfiguration;
import org.apache.commons.configuration.XMLConfiguration;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;


public class ImageQAPluginTest {
    
    File configFile = new File("test/test_config");
    File imageFolder = new File("test/sample_images");

    @Before
    public void setUp() throws Exception {
    }

    @After
    public void tearDown() throws Exception {
    }

    @Test
    public void testSortImages() throws ConfigurationException {
        ImageQAPlugin plugin = new ImageQAPlugin();
        XMLConfiguration config =  new XMLConfiguration(configFile);
        SubnodeConfiguration myConfig = config.configurationAt("config");
        plugin.initConfig(myConfig);
        plugin.initImageList(myConfig, imageFolder.getAbsolutePath());
        
        Assert.assertEquals(12, plugin.getAllImages().size(), 0);
    }

}
