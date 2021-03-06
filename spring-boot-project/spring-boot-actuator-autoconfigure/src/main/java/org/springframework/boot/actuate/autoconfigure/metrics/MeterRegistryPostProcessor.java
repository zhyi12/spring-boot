/*
 * Copyright 2012-2018 the original author or authors.
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

package org.springframework.boot.actuate.autoconfigure.metrics;

import java.util.Collection;
import java.util.Collections;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.util.LambdaSafe;

/**
 * {@link BeanPostProcessor} to apply {@link MeterRegistryCustomizer customizers} and
 * {@link MeterBinder binters} and {@link Metrics#addRegistry global registration} to
 * {@link MeterRegistry meter registries}. This post processor intentionally skips
 * {@link CompositeMeterRegistry} with the assumptions that the registries it contains are
 * beans and will be customized directly.
 *
 * @author Jon Schneider
 * @author Phillip Webb
 */
class MeterRegistryPostProcessor implements BeanPostProcessor {

	private final Collection<MeterRegistryCustomizer<?>> customizers;

	private final Collection<MeterBinder> binders;

	private final boolean addToGlobalRegistry;

	MeterRegistryPostProcessor(ObjectProvider<Collection<MeterBinder>> binders,
			ObjectProvider<Collection<MeterRegistryCustomizer<?>>> customizers,
			boolean addToGlobalRegistry) {
		this.binders = binders.getIfAvailable(Collections::emptyList);
		this.customizers = customizers.getIfAvailable(Collections::emptyList);
		this.addToGlobalRegistry = addToGlobalRegistry;
	}

	@Override
	public Object postProcessBeforeInitialization(Object bean, String beanName) {
		return bean;
	}

	@Override
	public Object postProcessAfterInitialization(Object bean, String beanName) {
		if (bean instanceof MeterRegistry) {
			postProcess((MeterRegistry) bean);
		}
		return bean;
	}

	private void postProcess(MeterRegistry registry) {
		if (registry instanceof CompositeMeterRegistry) {
			return;
		}
		// Customizers must be applied before binders, as they may add custom tags or
		// alter timer or summary configuration.
		customize(registry);
		addBinders(registry);
		if (this.addToGlobalRegistry && registry != Metrics.globalRegistry) {
			Metrics.addRegistry(registry);
		}
	}

	@SuppressWarnings("unchecked")
	private void customize(MeterRegistry registry) {
		LambdaSafe.callbacks(MeterRegistryCustomizer.class, this.customizers, registry)
				.withLogger(MeterRegistryPostProcessor.class)
				.invoke((customizer) -> customizer.customize(registry));
	}

	private void addBinders(MeterRegistry registry) {
		this.binders.forEach((binder) -> binder.bindTo(registry));
	}

}
