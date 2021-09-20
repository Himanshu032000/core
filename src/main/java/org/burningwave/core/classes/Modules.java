/*
 * This file is part of Burningwave Core.
 *
 * Author: Roberto Gentili
 *
 * Hosted at: https://github.com/burningwave/core
 *
 * --
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2019-2021 Roberto Gentili
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without
 * limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO
 * EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE
 * OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.burningwave.core.classes;


import static org.burningwave.core.assembler.StaticComponentContainer.Fields;
import static org.burningwave.core.assembler.StaticComponentContainer.Methods;
import static org.burningwave.core.assembler.StaticComponentContainer.Strings;
import static org.burningwave.core.assembler.StaticComponentContainer.Throwables;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.burningwave.core.function.TriConsumer;

@SuppressWarnings("unchecked")
public class Modules {
	private Class<?> moduleClass;
	private Set<?> allSet = new HashSet<>();
	private Set<?> everyOneSet = new HashSet<>();
	private Set<?> allUnnamedSet = new HashSet<>();
	private Map<String, ?> nameToModule;
	
	Modules() {
		try {
			moduleClass = Class.forName("java.lang.Module");
			Class<?> moduleLayerClass = Class.forName("java.lang.ModuleLayer");
			Object moduleLayer = Methods.invokeStaticDirect(moduleLayerClass, "boot");
			nameToModule = Fields.getDirect(moduleLayer, "nameToModule");
			allSet = new HashSet<>();
			allSet.add(Fields.getStaticDirect(moduleClass, "ALL_UNNAMED_MODULE"));
			allSet.add(Fields.getStaticDirect(moduleClass, "EVERYONE_MODULE"));
			everyOneSet = new HashSet<>();
			everyOneSet.add(Fields.getStaticDirect(moduleClass, "EVERYONE_MODULE"));
			allUnnamedSet = new HashSet<>();
			allUnnamedSet.add(Fields.getStaticDirect(moduleClass, "ALL_UNNAMED_MODULE"));
		} catch (Throwable exc) {
			Throwables.throwException(exc);
		}
	}
	
	public static Modules create() {
		return new Modules();
	}
	
	public void exportAllToAll() {
		try {
			nameToModule.forEach((name, module) -> {
				((Set<String>)Methods.invokeDirect(module, "getPackages")).forEach(pkgName -> {
					exportToAll("exportedPackages", module, pkgName);
					exportToAll("openPackages", module, pkgName);
				});
			});
		} catch (Throwable exc) {
			Throwables.throwException(exc);
		}
	}
	
	
	public void exportToAllUnnamed(String name) {
		exportTo(name, this::exportToAllUnnamed);
	}
	
	
	public void exportToAll(String name) {
		exportTo(name, this::exportToAll);
	}
	
	public void export(String from, String to) {
		try {
			Object moduleFrom = checkAndGetModule(from);
			Object moduleTo = checkAndGetModule(to);
			((Set<String>)Methods.invokeDirect(moduleFrom, "getPackages")).forEach(pkgName -> {
				export("exportedPackages", moduleFrom, pkgName, moduleTo);
				export("exportedPackages", moduleFrom, pkgName, moduleTo);
			});
		} catch (Throwable exc) {
			Throwables.throwException(exc);
		}
	}
	

	Object checkAndGetModule(String from) {
		Object module = nameToModule.get(from);
		if (module == null) {
			throw new NotFoundException(Strings.compile("Module named name {} not found", from));
		}
		return module;
	}
	
	public void exportTo(String name, TriConsumer<String, Object, String> exporter) {
		try {
			Object module = checkAndGetModule(name);
			((Set<String>)Methods.invokeDirect(module, "getPackages")).forEach(pkgName -> {
				exporter.accept("exportedPackages", module, pkgName);
				exporter.accept("openPackages", module, pkgName);
			});
		} catch (Throwable exc) {
			Throwables.throwException(exc);
		}
	}
	
	
	void exportToAll(String fieldName, Object module, String pkgName) {
		Map<String, Set<?>> pckgForModule = Fields.getDirect(module, fieldName);
		if (pckgForModule == null) {
			pckgForModule = new HashMap<>();
			Fields.setDirect(module, fieldName, pckgForModule);
		}
		pckgForModule.put(pkgName, allSet);
		if (fieldName.startsWith("exported")) {	
			Methods.invokeStaticDirect(moduleClass, "addExportsToAll0", module, pkgName);
		}
	}
	
	
	void exportToAllUnnamed(String fieldName, Object module, String pkgName) {
		Map<String, Set<?>> pckgForModule = Fields.getDirect(module, fieldName);
		if (pckgForModule == null) {
			pckgForModule = new HashMap<>();
			Fields.setDirect(module, fieldName, pckgForModule);
		}
		pckgForModule.put(pkgName, allUnnamedSet);
		if (fieldName.startsWith("exported")) {	
			Methods.invokeStaticDirect(moduleClass, "addExportsToAllUnnamed0", module, pkgName);
		}
	}
	
	void export(String fieldName, Object moduleFrom, String pkgName, Object moduleTo) {
		Map<String, Set<?>> pckgForModule = Fields.getDirect(moduleFrom, fieldName);
		if (pckgForModule == null) {
			pckgForModule = new HashMap<>();
			Fields.setDirect(moduleFrom, fieldName, pckgForModule);
		}
		Set<Object> moduleSet = (Set<Object>)pckgForModule.get(pkgName);
		if (!(moduleSet instanceof HashSet)) {
			if (moduleSet != null) {
				moduleSet = new HashSet<>(moduleSet);
			} else {
				moduleSet = new HashSet<>();
			}			
			pckgForModule.put(pkgName, moduleSet);
		}
		moduleSet.add(moduleTo);
		if (fieldName.startsWith("exported")) {	
			Methods.invokeStaticDirect(moduleClass, "addExports0", moduleFrom, pkgName, moduleTo);
		}		
	}


	public static class NotFoundException extends RuntimeException {

		private static final long serialVersionUID = 3095842376538548262L;
		
		public NotFoundException(String message) {
	        super(message);
	    }
		
	}
}
