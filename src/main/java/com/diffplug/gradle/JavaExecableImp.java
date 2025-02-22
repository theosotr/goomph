/*
 * Copyright 2019 DiffPlug
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.diffplug.gradle;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Consumer;

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.JavaExec;
import org.gradle.process.ExecResult;
import org.gradle.process.JavaExecSpec;

import com.diffplug.common.base.Throwing;
import com.diffplug.common.base.Unhandled;

/** Private implementation details. */
class JavaExecableImp {
	/** @see #exec(Project, JavaExecable, com.diffplug.common.base.Throwing.Consumer) */
	@SuppressWarnings("unchecked")
	static <T extends JavaExecable> T execInternal(T input, FileCollection classpath, Action<JavaExecSpec> settings, Throwing.Function<Action<JavaExecSpec>, ExecResult> javaExecer) throws Throwable {
		File tempFile = File.createTempFile("JavaExecOutside", ".temp");
		try {
			// write the input object to a file
			SerializableMisc.write(tempFile, input);
			ExecResult execResult = javaExecer.apply(execSpec -> {
				// let the user change things
				settings.execute(
						// use the main below as the main
						execSpec.setMain(JavaExecable.class.getName())
								// pass the input object to the main
								.args(tempFile.getAbsolutePath())
								// set the nominal classpath
								.setClasspath(classpath));
			});
			execResult.rethrowFailure();
			// load the resultant object after it has been executed and resaved
			Object result = SerializableMisc.read(tempFile);
			if (result instanceof JavaExecable) {
				return (T) result;
			} else if (result instanceof Throwable) {
				// rethrow any exceptions, if there were any
				throw (Throwable) result;
			} else {
				throw Unhandled.classException(result);
			}
		} finally {
			FileMisc.forceDelete(tempFile); // delete the temp
		}
	}

	static Set<File> fromLocalClassloader() {
		Set<File> files = new LinkedHashSet<>();
		Consumer<Class<?>> addPeerClasses = clazz -> {
			URLClassLoader urlClassloader = (URLClassLoader) clazz.getClassLoader();
			for (URL url : urlClassloader.getURLs()) {
				String name = url.getFile();
				if (name != null) {
					files.add(new File(name));
				}
			}
		};
		// add the classes that goomph needs
		addPeerClasses.accept(JavaExecable.class);
		// add the gradle API
		addPeerClasses.accept(JavaExec.class);
		return files;
	}
}
