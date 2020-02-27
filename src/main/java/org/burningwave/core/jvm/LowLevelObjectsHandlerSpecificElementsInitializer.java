package org.burningwave.core.jvm;

import static org.burningwave.core.assembler.StaticComponentsContainer.JVMInfo;
import static org.burningwave.core.assembler.StaticComponentsContainer.Throwables;

import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;

import org.burningwave.core.Component;

import sun.misc.Unsafe;

@SuppressWarnings("restriction")
abstract class LowLevelObjectsHandlerSpecificElementsInitializer implements Component {
	LowLevelObjectsHandler lowLevelObjectsHandler;
	
	LowLevelObjectsHandlerSpecificElementsInitializer(LowLevelObjectsHandler lowLevelObjectsHandler) {
		this.lowLevelObjectsHandler = lowLevelObjectsHandler;
		try {
			Field theUnsafeField = Unsafe.class.getDeclaredField("theUnsafe");
			theUnsafeField.setAccessible(true);
			this.lowLevelObjectsHandler.unsafe = (Unsafe)theUnsafeField.get(null);
		} catch (Throwable exc) {
			logInfo("Exception while retrieving unsafe");
			throw Throwables.toRuntimeException(exc);
		}
	}
	
	
	void init() {
		initEmptyMembersArrays();
		initMembersRetrievers();
		initSpecificElements();
	}


	private void initEmptyMembersArrays() {
		lowLevelObjectsHandler.emtpyFieldsArray = new Field[]{};
		lowLevelObjectsHandler.emptyMethodsArray = new Method[]{};
		lowLevelObjectsHandler.emptyConstructorsArray = new Constructor<?>[]{};
	}
	
	public static void build(LowLevelObjectsHandler lowLevelObjectsHandler) {
		try (LowLevelObjectsHandlerSpecificElementsInitializer initializer =
				JVMInfo.getVersion() > 8 ?
				new LowLevelObjectsHandlerSpecificElementsInitializer4Java9(lowLevelObjectsHandler):
				new LowLevelObjectsHandlerSpecificElementsInitializer4Java8(lowLevelObjectsHandler)) {
			initializer.init();
		}
	}
	
	private void initMembersRetrievers() {
		try {
			Lookup consulter = lowLevelObjectsHandler.consulterRetriever.apply(Class.class);
			lowLevelObjectsHandler.getDeclaredFieldsRetriever = consulter.findSpecial(
				Class.class, "getDeclaredFields0",
				MethodType.methodType(Field[].class, boolean.class),
				Class.class
			);
			
			lowLevelObjectsHandler.getDeclaredMethodsRetriever = consulter.findSpecial(
				Class.class,
				"getDeclaredMethods0",
				MethodType.methodType(Method[].class, boolean.class),
				Class.class
			);

			lowLevelObjectsHandler.getDeclaredConstructorsRetriever = consulter.findSpecial(
				Class.class,
				"getDeclaredConstructors0",
				MethodType.methodType(Constructor[].class, boolean.class),
				Class.class
			);
			lowLevelObjectsHandler.parentClassLoaderFields = new ConcurrentHashMap<>();
		} catch (Throwable exc) {
			throw Throwables.toRuntimeException(exc);
		}
	}
	
	abstract void initSpecificElements();
	
	@Override
	public void close() {
		this.lowLevelObjectsHandler = null;
	}
}
