package org.burningwave.core;

import static org.burningwave.core.assembler.StaticComponentContainer.BackgroundExecutor;
import static org.burningwave.core.assembler.StaticComponentContainer.GlobalProperties;

import org.burningwave.core.assembler.ComponentContainer;
import org.junit.jupiter.api.Test;

public class ComponentContainerTest extends BaseTest {

	
	@Test
	public void reInitAndCloseTest() {
		testDoesNotThrow(() -> {
			ComponentContainer componentSupplier = ComponentContainer.create("burningwave.properties");
			componentSupplier.getClassFactory();
			componentSupplier.getClassHunter();
			componentSupplier.getClassPathHunter();
			componentSupplier.getCodeExecutor();
			componentSupplier.getPathHelper();
			GlobalProperties.put("newPropertyName", "newPropertyValue");
			componentSupplier.reInit();
			componentSupplier.getClassFactory();
			componentSupplier.getClassHunter();
			componentSupplier.getClassPathHunter();
			componentSupplier.getCodeExecutor();
			componentSupplier.getPathHelper();
			componentSupplier.close();
		});
	}
	
	@Test
	public void clearAll() {
		testDoesNotThrow(() -> {
			logWarn("Total memory before clearAll {}", Runtime.getRuntime().totalMemory());
			ComponentContainer.clearAll();
			BackgroundExecutor.waitForExecutablesEnding();
			System.gc();
			logWarn("Total memory after clearAll {}", Runtime.getRuntime().totalMemory());
		});
	}
	
	
	@Test
	public void reInit() {
		testDoesNotThrow(() -> {
			getComponentSupplier().reInit();
		});
	}
}
