/*
 * Copyright 2013 the original author or authors.
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

package org.springframework.xd.dirt.plugins.job;

import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.batch.core.JobParameter;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.converter.DefaultJobParametersConverter;
import org.springframework.batch.core.converter.JobParametersConverter;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.MapType;

/**
 * More flexible implementation of the {@link JobParametersConverter}. Allows to convert a wide variety of object types
 * to {@link JobParameters}.
 * 
 * @author Gunnar Hillert
 * @since 1.0
 * 
 */
public class ExpandedJobParametersConverter extends DefaultJobParametersConverter {

	protected final Log logger = LogFactory.getLog(getClass());

	public static final String ABSOLUTE_FILE_PATH = "absoluteFilePath";

	public static final String UNIQUE_JOB_PARAMETER_KEY = "random";

	private volatile boolean makeParametersUnique = true;

	private final ObjectMapper objectMapper = new ObjectMapper();

	/**
	 * Will set the {@link DateFormat} on the underlying {@link DefaultJobParametersConverter}. If not set explicitly
	 * the {@link DateFormat} will default to "yyyy/MM/dd".
	 * 
	 * @param dateFormat Must not be null
	 */
	@Override
	public void setDateFormat(DateFormat dateFormat) {
		Assert.notNull(dateFormat, "The provided dateFormat must not be null.");
		super.setDateFormat(dateFormat);
	}

	/**
	 * Allows for setting the {@link DateFormat} using a {@link String}.
	 * 
	 * @param dateFormatAsString Will be ignored if null or empty.
	 */
	public void setDateFormatAsString(String dateFormatAsString) {
		if (StringUtils.hasText(dateFormatAsString)) {
			super.setDateFormat(new SimpleDateFormat(dateFormatAsString));
		}
	}

	/**
	 * If not set, this property defaults to <code>true</code>.
	 * 
	 * @param makeParametersUnique If not set defaults to {@code true}
	 */
	public void setMakeParametersUnique(boolean makeParametersUnique) {
		this.makeParametersUnique = makeParametersUnique;
	}

	/**
	 * Setter for the {@link NumberFormat} which is set on the underlying {@link DefaultJobParametersConverter}. If not
	 * set explicitly, defaults to {@code NumberFormat.getInstance(Locale.US);}
	 * 
	 * @param numberFormat Must not be null.
	 */
	@Override
	public void setNumberFormat(NumberFormat numberFormat) {
		Assert.notNull(numberFormat, "The provided numberFormat must not be null.");
		super.setNumberFormat(numberFormat);
	}

	/**
	 * Allows for setting the {@link NumberFormat} using a {@link String}. The passed-in String will be converted to a
	 * {@link DecimalFormat}.
	 * 
	 * @param numberFormatAsString Will be ignored if null or empty.
	 */
	public void setNumberFormatAsString(String numberFormatAsString) {
		if (StringUtils.hasText(numberFormatAsString)) {
			super.setNumberFormat(new DecimalFormat(numberFormatAsString));
		}
	}

	/**
	 * Return {@link JobParameters} for the passed-in {@link File}. Will set the {@link JobParameter} with key
	 * {@link #ABSOLUTE_FILE_PATH} to the {@link File}'s absolutePath. Method will ultimately call
	 * {@link #getJobParameters(Properties)}.
	 * 
	 * @param file Must not be null.
	 */
	public JobParameters getJobParametersForFile(File file) {
		Assert.notNull(file, "The provided file must not be null.");
		final Properties parametersAsProperties = new Properties();
		parametersAsProperties.put(ABSOLUTE_FILE_PATH, file.getAbsolutePath());
		return this.getJobParameters(parametersAsProperties);
	}

	/**
	 * Converts a {@link String}-based JSON map to {@link JobParameters}. The String is converted using Jackson's
	 * {@link ObjectMapper}.
	 * 
	 * The method will ultimately call {@link #getJobParametersForMap(Map)}.
	 * 
	 * @param jobParametersAsJsonMap Can be null or empty.
	 */
	public JobParameters getJobParametersForJsonString(String jobParametersAsJsonMap) {

		final Map<String, Object> parameters;

		if (jobParametersAsJsonMap != null && !jobParametersAsJsonMap.isEmpty()) {

			final MapType mapType = objectMapper.getTypeFactory().constructMapType(HashMap.class, String.class,
					String.class);

			try {
				parameters = new ObjectMapper().readValue(jobParametersAsJsonMap, mapType);
			}
			catch (IOException e) {
				throw new IllegalArgumentException("Unable to convert provided JSON to Map<String, Object>", e);
			}

			if (this.makeParametersUnique && parameters.containsKey(UNIQUE_JOB_PARAMETER_KEY)) {
				throw new IllegalStateException(String.format(
						"Parameter '%s' is already used to identify uniqueness for the executing Batch job.",
						UNIQUE_JOB_PARAMETER_KEY));
			}
		}
		else {
			parameters = null;
		}

		return getJobParametersForMap(parameters);
	}

	/**
	 * Will convert the provided {@link Map} into {@link JobParameters}. The method will ultimately call
	 * {@link #getJobParameters(Properties)}.
	 * 
	 * @param map Can be null or an empty {@link Map}.
	 */
	public JobParameters getJobParametersForMap(Map<?, ?> map) {

		final Properties parametersAsProperties = new Properties();

		if (map != null) {
			parametersAsProperties.putAll(map);
		}

		return this.getJobParameters(parametersAsProperties);
	}

	/**
	 * If {@link #makeParametersUnique} is {@code true} the {@link JobParameter} with key
	 * {@link #UNIQUE_JOB_PARAMETER_KEY} will be added with a random number value.
	 * 
	 * The method will ultimately call {@link DefaultJobParametersConverter#getJobParameters(Properties)}.
	 * 
	 * @param properties Can be null.
	 */
	@Override
	public JobParameters getJobParameters(Properties properties) {

		final Properties localProperties;

		if (properties != null) {
			localProperties = properties;
		}
		else {
			localProperties = new Properties();
		}

		if (this.makeParametersUnique) {
			localProperties.put(UNIQUE_JOB_PARAMETER_KEY, String.valueOf(Math.random()));
		}
		return super.getJobParameters(localProperties);
	}
}
