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
package org.burningwave.core.io;

import static org.burningwave.core.assembler.StaticComponentContainer.BackgroundExecutor;
import static org.burningwave.core.assembler.StaticComponentContainer.BufferHandler;
import static org.burningwave.core.assembler.StaticComponentContainer.Cache;
import static org.burningwave.core.assembler.StaticComponentContainer.Driver;
import static org.burningwave.core.assembler.StaticComponentContainer.ManagedLoggersRepository;
import static org.burningwave.core.assembler.StaticComponentContainer.Paths;
import static org.burningwave.core.assembler.StaticComponentContainer.Streams;
import static org.burningwave.core.assembler.StaticComponentContainer.Synchronizer;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.zip.ZipException;

import org.burningwave.core.concurrent.QueuedTasksExecutor;
import org.burningwave.core.function.Executor;
import org.burningwave.core.io.ZipInputStream.Entry.Attached;

@SuppressWarnings("unchecked")
public class ZipInputStream extends java.util.zip.ZipInputStream implements IterableZipContainer {
	String absolutePath;
	String conventionedAbsolutePath;
	IterableZipContainer parent;
	IterableZipContainer.Entry currentZipEntry;
	ByteBufferInputStream byteBufferInputStream;
	
	
	ZipInputStream(String absolutePath, InputStream inputStream) {
		super(inputStream);
		this.absolutePath = absolutePath;
	}

	ZipInputStream(String absolutePath, ByteBufferInputStream inputStream) {
		super(inputStream);
		this.absolutePath = absolutePath;
		this.byteBufferInputStream = inputStream;
	}

	@Override
	public Function<IterableZipContainer.Entry, org.burningwave.core.io.IterableZipContainer.Entry> getEntrySupplier() {
		return Entry.Detached::new;
	}

	@Override
	public IterableZipContainer getParent() {
		if (conventionedAbsolutePath == null) {
			getConventionedAbsolutePath();
		}
		return parent;
	}
	
	@Override
	public <T> Collection<T> findAllAndConvert(Supplier<Collection<T>> supplier,
		Predicate<IterableZipContainer.Entry> zipEntryPredicate,
		Function<IterableZipContainer.Entry, T> tSupplier,
		Predicate<IterableZipContainer.Entry> loadZipEntryData
	) {
		return Synchronizer.execute(IterableZipContainer.classId + "_" + getAbsolutePath(), () -> {
//			QueuedTasksExecutor.ProducerTask<Collection<T>> task = BackgroundExecutor.createProducerTask(() -> {
				return IterableZipContainer.super.findAllAndConvert(supplier, zipEntryPredicate, tSupplier, loadZipEntryData);
//			}).submit();
//			if (task.waitForFinish(60000).hasFinished()) {
//				if (task.getException() == null) {
//					return task.join();
//				}
//				return Driver.throwException(task.getException());
//			} else {
//				ManagedLoggersRepository.logError(
//					getClass()::getName, "Unable to find and convert entries for {}{}",
//					getAbsolutePath(),
//					task.getInfoAsString()
//				);
//				return Driver.throwException("Unable to find and convert entries for {}", getAbsolutePath());
//			}			
		});
	}
	
	@Override
	public <T> T findFirstAndConvert(
		Predicate<IterableZipContainer.Entry> zipEntryPredicate,
		Function<IterableZipContainer.Entry, T> tSupplier,
		Predicate<IterableZipContainer.Entry> loadZipEntryData
	) {
		return Synchronizer.execute(IterableZipContainer.classId + "_" + getAbsolutePath(), () -> {
//			QueuedTasksExecutor.ProducerTask<T> task = BackgroundExecutor.createProducerTask(() -> {
				return IterableZipContainer.super.findFirstAndConvert(zipEntryPredicate, tSupplier, loadZipEntryData);
//			}).submit();
//			if (task.waitForFinish(60000).hasFinished()) {
//				if (task.getException() == null) {
//					return task.join();
//				}
//				return Driver.throwException(task.getException());
//			} else {
//				ManagedLoggersRepository.logError(
//					getClass()::getName, "Unable to find and convert entry for {}{}",
//					getAbsolutePath(),
//					task.getInfoAsString()
//				);
//				return Driver.throwException("Unable to find and convert entry for {}", getAbsolutePath());
//			}
		});
	}
	
	
	@Override
	public String getAbsolutePath() {
		return absolutePath;
	}


	@Override
	public String getConventionedAbsolutePath() {
		if (conventionedAbsolutePath == null) {
			synchronized (this) {
				if (parent != null) {
					conventionedAbsolutePath = parent.getConventionedAbsolutePath() + absolutePath.replace(parent.getAbsolutePath() + "/", "");
				} else {
					FileSystemItem zipFis = FileSystemItem.ofPath(absolutePath);
					if (zipFis.getParentContainer().isArchive()) {
						parent = IterableZipContainer.create(zipFis.getParentContainer().getAbsolutePath());
						return getConventionedAbsolutePath();
					} else {
						conventionedAbsolutePath = absolutePath;
					}
				}
				conventionedAbsolutePath += IterableZipContainer.PATH_SUFFIX;
			}
		}
		return conventionedAbsolutePath;
	}

	@Override
	public ByteBuffer toByteBuffer() {
		return byteBufferInputStream.toByteBuffer();
	}

	public byte[] toByteArray() {
		return BufferHandler.toByteArray(toByteBuffer());
	}

	@Override
    protected Entry.Attached createZipEntry(String name) {
		return new Entry.Attached(name, this);
    }


	@Override
	public Entry.Attached getNextEntry() {
		return (Attached)getNextEntry((zEntry) -> false);
	}

	@Override
	public synchronized IterableZipContainer.Entry getNextEntry(Predicate<IterableZipContainer.Entry> loadZipEntryData) {
		Executor.run(() -> {
			try {
				currentZipEntry = (Entry.Attached)super.getNextEntry();
			} catch (ZipException exc) {
				String message = exc.getMessage();
				ManagedLoggersRepository.logWarn(getClass()::getName, "Could not open zipEntry of {}: {}", absolutePath, message);
			}
		});
		if (currentZipEntry != null && loadZipEntryData.test(currentZipEntry)) {
			currentZipEntry.toByteBuffer();
		}
		return currentZipEntry;
	}

	public synchronized IterableZipContainer.Entry getNextEntryAsDetached() {
		return getNextEntryAsDetached(zEntry -> false);
	}

	public IterableZipContainer.Entry getNextEntryAsDetached(Predicate<IterableZipContainer.Entry> loadZipEntryData) {
		return Optional.ofNullable(
			getNextEntry(loadZipEntryData)).map(zipEntry ->	((Attached) zipEntry).convert()
		).orElseGet(
			() -> null
		);
	}

	@Override
	public IterableZipContainer.Entry getCurrentZipEntry() {
		return currentZipEntry;
	}

	public IterableZipContainer.Entry convertCurrentZipEntry() {
		return ((Attached)getCurrentZipEntry()).convert();
	}


	@Override
	public synchronized void closeEntry() {
		if (currentZipEntry != null) {
			try {
				super.closeEntry();
			} catch (IOException exc) {
				ManagedLoggersRepository.logWarn(getClass()::getName, "Exception occurred while closing zipEntry {}: {}", Optional.ofNullable(getCurrentZipEntry()).map((zipEntry) -> zipEntry.getAbsolutePath()).orElseGet(() -> "null"), exc.getMessage());
			}
			currentZipEntry.close();
			currentZipEntry = null;
		}
	}
	
	@Override
	public void close() {
		closeEntry();
		parent = null;
		absolutePath = null;
		Executor.run(() -> super.close());
		this.byteBufferInputStream = null;
	}

	public static interface Entry extends IterableZipContainer.Entry {

		static class Attached extends java.util.zip.ZipEntry implements Entry {
			private ZipInputStream zipInputStream;
			private String absolutePath;
			private String cleanedName;
			private Boolean archive;

			Attached(Entry.Attached e, ZipInputStream zIS) {
				super(e);
				this.zipInputStream = zIS;
			}

			Attached(String name, ZipInputStream zIS) {
				super(name);
				this.zipInputStream = zIS;
			}

			@Override
			public boolean isArchive() {
				if (archive != null) {
					return archive;
				}
				ByteBuffer content = toByteBuffer();
				return archive = content != null ? Streams.isArchive(content) : false;
			}

			@Override
			public IterableZipContainer getParentContainer() {
				return zipInputStream;
			}

			@Override
			public String getCleanedName() {
				if (cleanedName != null) {
					return cleanedName;
				}
				String cleanedName = super.getName();
				if (!cleanedName.startsWith("/")) {
					this.cleanedName = cleanedName;
				} else {
					if (!cleanedName.equals("/")) {
						this.cleanedName =  cleanedName.substring(1, cleanedName.length());
					} else {
						this.cleanedName = "";
					}
				}
				return this.cleanedName;
			}

			@Override
			public String getAbsolutePath() {
				if (absolutePath != null) {
					return absolutePath;
				}
				return absolutePath = Paths.clean(zipInputStream.getAbsolutePath() + "/" + getName());
			}

			@Override
			public long getSize() {
				long size = super.getSize();
				if (size < 0) {
					size = BufferHandler.limit(toByteBuffer());
				}
				return size;
			}


			private ByteBuffer loadContent() {
				ByteBuffer content = Cache.pathForContents.get(getAbsolutePath());
				if (content != null) {
					return content;
				}
				if (zipInputStream.getCurrentZipEntry() != this) {
					Driver.throwException("{} and his ZipInputStream are not aligned", Attached.class.getSimpleName());
				}
				AtomicReference<ByteBuffer> contentWrapper = new AtomicReference<>();
				try {
					contentWrapper.set(BufferHandler.shareContent(Streams.toByteBuffer(zipInputStream, (int)super.getSize())));
				} catch (Throwable exc) {
					ManagedLoggersRepository.logError(getClass()::getName, "Could not load content of {} of {}", exc, getName(), zipInputStream.getAbsolutePath());
					return null;
				}
				return Cache.pathForContents.getOrUploadIfAbsent(
					getAbsolutePath(), () -> {
						return contentWrapper.get();
					}
				);
			}

			@Override
			public ByteBuffer toByteBuffer() {
				return loadContent();
			}

			public IterableZipContainer.Entry convert() {
				return new Entry.Detached(
					this
				);
			}

			public void unzipToFolder(File folder) {
				File destinationFilePath = new File(folder.getAbsolutePath(), this.getName());
				int defaultBufferSize = BufferHandler.getDefaultBufferSize();
				destinationFilePath.getParentFile().mkdirs();
				if (!this.isDirectory()) {
					Executor.run(() -> {
						try (BufferedInputStream bis = new BufferedInputStream(this.toInputStream())) {
							int byteTransferred = 0;
							byte buffer[] = new byte[defaultBufferSize];
							try (
								FileOutputStream fos = FileOutputStream.create(destinationFilePath);
								BufferedOutputStream bos = new BufferedOutputStream(fos, defaultBufferSize)
							) {
								while ((byteTransferred = bis.read(buffer, 0, defaultBufferSize)) != -1) {
									bos.write(buffer, 0, byteTransferred);
								}
								bos.flush();
							}
						}
					});
				}
			}

			@Override
			public void close() {
				zipInputStream = null;
				absolutePath = null;
				cleanedName = null;
				archive = null;
			}
		}

		public static class Detached implements Entry {
			private String name;
			private String cleanedName;
			private String absolutePath;
			private IterableZipContainer zipInputStream;
			private Boolean archive;

			Detached(IterableZipContainer.Entry zipEntry) {
				this.name = zipEntry.getName();
				this.absolutePath = zipEntry.getAbsolutePath();
				this.zipInputStream = zipEntry.getParentContainer().duplicate();
			}

			@Override
			public boolean isArchive() {
				if (archive != null) {
					return archive;
				}
				ByteBuffer content = toByteBuffer();
				return archive = content != null ? Streams.isArchive(content) : false;
			}

			@Override
			public IterableZipContainer getParentContainer() {
				return zipInputStream.duplicate();
			}

			@Override
			public ByteBuffer toByteBuffer() {
				ByteBuffer content = Cache.pathForContents.get(getAbsolutePath());
				if (content != null) {
					return content;
				}
				AtomicReference<ByteBuffer> contentWrapper = new AtomicReference<>();
				try (IterableZipContainer zipInputStream = getParentContainer()) {
					contentWrapper.set(
						BufferHandler.shareContent(
							zipInputStream.findFirstAndConvert((entry) ->
								entry.getName().equals(getName()), zEntry ->
								zEntry.toByteBuffer(), zEntry -> true
							)
						)
					);
				}
				return Cache.pathForContents.getOrUploadIfAbsent(
					getAbsolutePath(), () -> {
						return contentWrapper.get();
					}
				);
			}

			@Override
			public String getCleanedName() {
				if (cleanedName != null) {
					return cleanedName;
				}
				String cleanedName = name;
				if (!cleanedName.startsWith("/")) {
					this.cleanedName = cleanedName;
				} else {
					if (!cleanedName.equals("/")) {
						this.cleanedName =  cleanedName.substring(1, cleanedName.length());
					} else {
						this.cleanedName = "";
					}
				}
				return this.cleanedName;
			}

			@Override
			public String getName() {
				return name;
			}
			@Override
			public String getAbsolutePath() {
				return absolutePath;
			}

			@Override
			public boolean isDirectory() {
				return name.endsWith("/");
			}

			@Override
			public void close() {
				name = null;
				archive = null;
				cleanedName = null;
				absolutePath = null;
				zipInputStream.close();
				zipInputStream = null;
			}
		}
	}
}