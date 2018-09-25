package de.intranda.goobi.plugins;

import java.io.File;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.SubnodeConfiguration;
import org.apache.commons.configuration.XMLConfiguration;
import org.apache.commons.configuration.reloading.FileChangedReloadingStrategy;
import org.apache.commons.configuration.tree.xpath.XPathExpressionEngine;
import org.goobi.beans.Step;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class ImageQAPluginTest {

	File configFile = new File("test/test_config.xml").getAbsoluteFile();
	File imageFolder = new File("test/sample_images");
	ImageQAPlugin plugin = new ImageQAPlugin();
	SubnodeConfiguration myconfig = null;
	String projectName = "";
	String testStep = "";

	@Before
	public void setUp() throws Exception {
	}

	@After
	public void tearDown() throws Exception {
	}

	public void testSortImages() throws ConfigurationException {
		ImageQAPlugin plugin = new ImageQAPlugin();
		XMLConfiguration config = new XMLConfiguration(configFile);
		SubnodeConfiguration myConfig = (SubnodeConfiguration) config.configurationsAt("config").get(0);
		plugin.initConfig(myConfig);
		plugin.initImageList(myConfig, imageFolder.getAbsolutePath());

		Assert.assertEquals(12, plugin.getAllImages().size(), 0);
	}

	@Test
	public void testSortImagesNew() throws ConfigurationException {
		projectName = "configTestProject";
		testStep = "configTestStep";
		getConfig();
		plugin.initConfig(myconfig);
		plugin.initImageList(myconfig, imageFolder.getAbsolutePath());
		Assert.assertEquals(3, plugin.getAllImages().size(), 0);
	}

	/** test if setting for Display size is parsed and created correclty */
	@Test
	public void testDisplaySizes() throws ConfigurationException {
		projectName = "testDisplaySizesProject";
		testStep = "testDisplaySizesStep";

		getConfig();
		plugin.initConfig(myconfig);
		String sizes = "[{\"width\":800},{\"width\":1800},{\"width\":3000}]";
		Assert.assertEquals(sizes, plugin.getDisplaySizes());
	}

	/** Test if setting for Tile-sizes is parsed and created correctly */
	@Test
	public void testTileSize() throws ConfigurationException {
		projectName = "testTileSizeProject";
		testStep = "testTileSizeStep";
		getConfig();
		plugin.initConfig(myconfig);
		String tiles = "[{\"width\":256,\"scaleFactors\":[1,4,16,64]}]";
		Assert.assertEquals(tiles, plugin.getTileSize());
	}

	/** loads config file, needed for most tests */
	private void getConfig() throws ConfigurationException {
		plugin.setStep(new Step());

		XMLConfiguration xmlConfig = new XMLConfiguration(configFile);
		xmlConfig.setExpressionEngine(new XPathExpressionEngine());
		xmlConfig.setReloadingStrategy(new FileChangedReloadingStrategy());

		// order of configuration is:
		// 1.) project name and step name matches
		// 2.) step name matches and project is *
		// 3.) project name matches and step name is *
		// 4.) project name and step name are *
		try {
			myconfig = xmlConfig
					.configurationAt("//config[./project = '" + projectName + "'][./step = '" + testStep + "']");
		} catch (IllegalArgumentException e) {
			try {
				myconfig = xmlConfig.configurationAt("//config[./project = 'ImageQATest'][./step = 'ImageQA']");
			} catch (IllegalArgumentException e1) {
				try {
					myconfig = xmlConfig
							.configurationAt("//config[./project = 'My special project'][./step = 'MasterQA']");
				} catch (IllegalArgumentException e2) {
					myconfig = xmlConfig.configurationAt("//config[./project = '*'][./step = '*']");
				}
			}
		}
	}

}
