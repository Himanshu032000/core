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
 * Copyright (c) 2019 Roberto Gentili
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

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.burningwave.core.assembler.ComponentSupplier;
import org.burningwave.core.classes.ClassCriteria.TestContext;
import org.burningwave.core.io.FileSystemItem;
import org.burningwave.core.io.PathHelper;
import org.burningwave.core.iterable.Properties;

public class ClassPathHunter extends ClassPathScannerWithCachingSupport<Collection<Class<?>>, ClassPathHunter.SearchContext, ClassPathHunter.SearchResult> {
	
	public static class Configuration {
		
		public static class Key {
			
			public final static String NAME_IN_CONFIG_PROPERTIES = "class-path-hunter";
			public final static String DEFAULT_PATH_SCANNER_CLASS_LOADER = NAME_IN_CONFIG_PROPERTIES + ".default-path-scanner-class-loader";
			public final static String PATH_SCANNER_CLASS_LOADER_SEARCH_CONFIG_CHECK_FILE_OPTIONS = NAME_IN_CONFIG_PROPERTIES + ".new-isolated-path-scanner-class-loader.search-config.check-file-option";
				
		}
		
		public final static Map<String, Object> DEFAULT_VALUES;
		
		static {
			Map<String, Object> defaultValues = new HashMap<>();
			
			defaultValues.put(Configuration.Key.DEFAULT_PATH_SCANNER_CLASS_LOADER + CodeExecutor.Configuration.Key.PROPERTIES_FILE_IMPORTS_SUFFIX,
				"${"+ CodeExecutor.Configuration.Key.COMMON_IMPORTS + "}" + CodeExecutor.Configuration.Value.CODE_LINE_SEPARATOR + 
				"${"+ Configuration.Key.DEFAULT_PATH_SCANNER_CLASS_LOADER + ".additional-imports}" + CodeExecutor.Configuration.Value.CODE_LINE_SEPARATOR +
				PathScannerClassLoader.class.getName() + ";"
			);
			defaultValues.put(Configuration.Key.DEFAULT_PATH_SCANNER_CLASS_LOADER + CodeExecutor.Configuration.Key.PROPERTIES_FILE_SUPPLIER_NAME_SUFFIX, ClassPathHunter.class.getPackage().getName() + ".DefaultPathScannerClassLoaderRetrieverForClassPathHunter");
			//DEFAULT_VALUES.put(Key.PARENT_CLASS_LOADER_FOR_PATH_SCANNER_CLASS_LOADER, "Thread.currentThread().getContextClassLoader()");
			defaultValues.put(
				Key.DEFAULT_PATH_SCANNER_CLASS_LOADER, 
				(Function<ComponentSupplier, ClassLoader>)(componentSupplier) -> 
					componentSupplier.getPathScannerClassLoader()
			);
			defaultValues.put(
				Key.PATH_SCANNER_CLASS_LOADER_SEARCH_CONFIG_CHECK_FILE_OPTIONS,
				"${" + ClassPathScannerAbst.Configuration.Key.DEFAULT_CHECK_FILE_OPTIONS + "}"
			);
			
			DEFAULT_VALUES = Collections.unmodifiableMap(defaultValues);
		}
	}
	
	private ClassPathHunter(
		PathHelper pathHelper,
		Object defaultPathScannerClassLoaderOrDefaultPathScannerClassLoaderSupplier,
		Properties config
	) {
		super(	
			pathHelper,
			(initContext) -> SearchContext._create(initContext),
			(context) -> new ClassPathHunter.SearchResult(context),
			defaultPathScannerClassLoaderOrDefaultPathScannerClassLoaderSupplier,
			config
		);
	}
	
	public static ClassPathHunter create(
		PathHelper pathHelper,
		Object defaultPathScannerClassLoaderOrDefaultPathScannerClassLoaderSupplier,
		Properties config
	) {
		return new ClassPathHunter(
			pathHelper,
			defaultPathScannerClassLoaderOrDefaultPathScannerClassLoaderSupplier,
			config
		);
	}
	
	@Override
	String getNameInConfigProperties() {
		return Configuration.Key.NAME_IN_CONFIG_PROPERTIES;
	}
	
	@Override
	String getDefaultPathScannerClassLoaderNameInConfigProperties() {
		return Configuration.Key.DEFAULT_PATH_SCANNER_CLASS_LOADER;
	}
	
	@Override
	String getDefaultPathScannerClassLoaderCheckFileOptionsNameInConfigProperties() {
		return Configuration.Key.PATH_SCANNER_CLASS_LOADER_SEARCH_CONFIG_CHECK_FILE_OPTIONS;
	}
	
	@Override
	<S extends SearchConfigAbst<S>> ClassCriteria.TestContext testCachedItem(
		SearchContext context, String baseAbsolutePath, String currentScannedItemAbsolutePath, Collection<Class<?>> classes
	) {
		ClassCriteria.TestContext testContext;
		for (Class<?> cls : classes) {
			if ((testContext = context.test(context.retrieveClass(cls))).getResult()) {
				return testContext;
			}
		}		
		return testContext = context.test(null);
	}
	
	@Override
	TestContext testPathAndCachedItem(
		SearchContext context,
		FileSystemItem[] cachedItemPathAndBasePath, 
		Collection<Class<?>> classes, 
		Predicate<FileSystemItem[]> fileFilterPredicate
	) {
		AtomicReference<ClassCriteria.TestContext> criteriaTestContextAR = new AtomicReference<>();
		cachedItemPathAndBasePath[0].findFirstInAllChildren(
			FileSystemItem.Criteria.forAllFileThat(
				(child, basePath) -> {
					boolean matchPredicate = false;
					if (matchPredicate = fileFilterPredicate.test(new FileSystemItem[]{child, basePath})) {
						criteriaTestContextAR.set(
							testCachedItem(
								context, cachedItemPathAndBasePath[1].getAbsolutePath(), cachedItemPathAndBasePath[0].getAbsolutePath(), classes
							)
						);
					}
					return matchPredicate;
				}
			)
		);
		return criteriaTestContextAR.get() != null? criteriaTestContextAR.get() : context.test(null);
	}
	
	@Override
	void iterateAndTestCachedPaths(
		SearchContext context,
		String basePath,
		Map<String, Collection<Class<?>>> itemsForPath,
		FileSystemItem.Criteria fileFilter
	) {
		if (fileFilter.hasNoExceptionHandler()) {
			fileFilter = fileFilter.createCopy().setDefaultExceptionHandler();
		}
		for (Entry<String, Collection<Class<?>>> cachedItemAsEntry : itemsForPath.entrySet()) {
			String absolutePathOfItem = cachedItemAsEntry.getKey();
			try {
				if (FileSystemItem.ofPath(absolutePathOfItem).findFirstInAllChildren(fileFilter) != null) {
					context.addItemFound(basePath, cachedItemAsEntry.getKey(), cachedItemAsEntry.getValue());
				}
			} catch (Throwable exc) {
				logError("Could not test cached entry of path " + absolutePathOfItem, exc);
			}
		}
	}
	
	@Override
	void addToContext(SearchContext context, TestContext criteriaTestContext,
		String basePath, FileSystemItem fileSystemItem, JavaClass javaClass
	) {
		String classPath = fileSystemItem.getAbsolutePath();
		FileSystemItem classPathAsFIS = FileSystemItem.ofPath(classPath.substring(0, classPath.lastIndexOf(javaClass.getPath())));
		context.addItemFound(basePath, classPathAsFIS.getAbsolutePath(), context.loadClass(javaClass.getName()));		
	}
	
	@Override
	public void close() {
		closeResources(() -> this.cache == null, () -> {
			super.close();
		});
	}
	
	public static class SearchContext extends org.burningwave.core.classes.SearchContext<Collection<Class<?>>> {
		
		SearchContext(InitContext initContext) {
			super(initContext);
		}		

		static SearchContext _create(InitContext initContext) {
			return new SearchContext(initContext);
		}

		
		void addItemFound(String basePathAsString, String classPathAsFile, Class<?> testedClass) {
			Map<String, Collection<Class<?>>> testedClassesForClassPathMap = retrieveCollectionForPath(
				itemsFoundMap,
				ConcurrentHashMap::new,
				basePathAsString
			);
			Collection<Class<?>> testedClassesForClassPath = testedClassesForClassPathMap.get(classPathAsFile);
			if (testedClassesForClassPath == null) {
				synchronized (testedClassesForClassPathMap) {
					testedClassesForClassPath = testedClassesForClassPathMap.get(classPathAsFile);
					if (testedClassesForClassPath == null) {
						testedClassesForClassPathMap.put(classPathAsFile, testedClassesForClassPath = ConcurrentHashMap.newKeySet());
					}
				}
			}
			testedClassesForClassPath.add(testedClass);
			itemsFoundFlatMap.putAll(testedClassesForClassPathMap);
		}
	}
	
	public static class SearchResult extends org.burningwave.core.classes.SearchResult<Collection<Class<?>>> {
		Collection<FileSystemItem> classPaths;
		
		public SearchResult(SearchContext context) {
			super(context);
		}
		
		public Collection<FileSystemItem> getClassPaths(ClassCriteria criteria) {
			ClassCriteria criteriaCopy = criteria.createCopy().init(
				context.getSearchConfig().getClassCriteria().getClassSupplier(),
				context.getSearchConfig().getClassCriteria().getByteCodeSupplier()
			);
			Optional.ofNullable(context.getSearchConfig().getClassCriteria().getClassesToBeUploaded()).ifPresent(classesToBeUploaded -> criteriaCopy.useClasses(classesToBeUploaded));
			Map<String, Collection<Class<?>>> itemsFound = new HashMap<>();
			getItemsFoundFlatMap().forEach((path, classColl) -> {
				for (Class<?> cls : classColl) {
					if (criteriaCopy.testWithFalseResultForNullEntityOrTrueResultForNullPredicate(cls).getResult()) {
						itemsFound.put(path, classColl);
						break;
					}
				}
			});
			criteriaCopy.close();
			return itemsFound.keySet().stream().map(absolutePath -> FileSystemItem.ofPath(absolutePath)).collect(Collectors.toCollection(HashSet::new));
		}
		
		public Collection<FileSystemItem> getClassPaths() {
			if (classPaths == null) {
				Map<String, Collection<Class<?>>> itemsFoundFlatMaps = context.getItemsFoundFlatMap();
				synchronized (itemsFoundFlatMaps) {
					if (classPaths == null) {
						classPaths = itemsFoundFlatMaps.keySet().stream().map(path -> 
							FileSystemItem.ofPath(path)
						).collect(
							Collectors.toCollection(
								HashSet::new
							)
						);
					}
				}
			}
			return classPaths;
		}
		
		@Override
		public void close() {
			classPaths = null;
			super.close();
		}
	}

}
