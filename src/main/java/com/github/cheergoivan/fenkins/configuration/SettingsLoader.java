package com.github.cheergoivan.fenkins.configuration;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import com.github.cheergoivan.fenkins.configuration.exception.IllegalSettingsException;
import com.github.cheergoivan.fenkins.configuration.exception.InitializationException;
import com.github.cheergoivan.fenkins.entity.settings.Settings;
import com.github.cheergoivan.fenkins.entity.settings.project.Project;
import com.github.cheergoivan.fenkins.service.id.IdGenerationService;
import com.github.cheergoivan.fenkins.util.file.FileUtils;
import com.github.cheergoivan.fenkins.util.serialization.SerializationUtils;
import com.github.cheergoivan.fenkins.util.yaml.YamlException;
import com.github.cheergoivan.fenkins.util.yaml.YamlUtils;

@Configuration
public class SettingsLoader {

	private static final Logger LOGGER = LoggerFactory.getLogger(SettingsLoader.class);

	@Autowired
	private IdGenerationService idGenerationService;

	@Bean
	@Profile("develop")
	public Settings loadSettings() {
		File storage = initializeStorage();
		return loadSettings(this.getClass().getResourceAsStream("/settings.yml"), storage);
	}

	@Bean
	@Profile("product")
	public Settings loadSettingsInProduct() {
		File storage = initializeStorage();
		try {
			return loadSettings(new FileInputStream(FenkinsProperties.SETTINGS_FILE), storage);
		} catch (FileNotFoundException e) {
			throw new InitializationException("Settings file doesn't exist!", e);
		}
	}

	private File initializeStorage() {
		File storage = FenkinsProperties.PROJECT_ID_STOREAGE;
		try {
			FileUtils.createFile(storage);
		} catch (IOException e) {
			throw new InitializationException("Fail to create a storage file!", e);
		}
		return storage;
	}

	private Settings loadSettings(InputStream in, File storage) {
		try {
			Settings settings = loadSettings(in);
			validateSettings(settings);
			Map<String, String> storedProjectIds = SerializationUtils.read(storage);
			if(storedProjectIds == null)
				storedProjectIds = new HashMap<>();
			Map<String, String> presentProjectIds = new HashMap<>();
			for (Project project : settings.getProjects()) {
				if (!storedProjectIds.containsKey(project.getName())) {
					String id = idGenerationService.generateId();
					presentProjectIds.put(project.getName(), id);
				} else {
					presentProjectIds.put(project.getName(), storedProjectIds.get(project.getName()));
				}
				project.setId(presentProjectIds.get(project.getName()));
			}
			SerializationUtils.write(presentProjectIds, storage, false);
			return settings;
		} catch (ClassNotFoundException | IOException e) {
			LOGGER.error(e.getMessage(), e);
			throw new IllegalSettingsException(e.getMessage());
		}
	}

	private Settings loadSettings(InputStream in) {
		try {
			return YamlUtils.load(in, Settings.class);
		} catch (YamlException e) {
			throw new IllegalSettingsException("Illegal settings!", e);
		}
	}

	private void validateSettings(Settings settings) {
		long names = settings.getProjects().stream().map(Project::getName).count();
		if (settings.getProjects().size() != names) {
			throw new IllegalSettingsException("Project name must be unique!");
		}
	}
}
