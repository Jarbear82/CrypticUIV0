# Nexus Note
---
A Visual Structured Notetaking Application.
- **Schema Mandatory:** Schemas provide note blueprints, creating a centralized,
approach to note-taking. This helps users avoid note duplication as well as fill in
missing information.  
- **Force-directed Graph:** Provides an at-a-glance comprehension of existing notes.
  While it can get messy, when viewing a large note graph, using certian queryies (or
  in the future, filters), allows the user to easily see and create connections between
  notes.
- **Query Terminal (Future Planned):** Provides an efficient way for advanced users to query and view
  desired portions of the graph.

**Intended Audiences:**
- Writers
- DnD Worldbuilding
- Anyone looking for a way to visualize data in a graph.

This is a Kotlin Multiplatform project targeting Desktop (JVM).

* [/composeApp](./composeApp/src) is for code that will be shared across your Compose Multiplatform applications.
  It contains several subfolders:
  - [commonMain](./composeApp/src/commonMain/kotlin) is for code that’s common for all targets.
  - Other folders are for Kotlin code that will be compiled for only the platform indicated in the folder name.
    For example, if you want to use Apple’s CoreCrypto for the iOS part of your Kotlin app,
    the [iosMain](./composeApp/src/iosMain/kotlin) folder would be the right place for such calls.
    Similarly, if you want to edit the Desktop (JVM) specific part, the [jvmMain](./composeApp/src/jvmMain/kotlin)
    folder is the appropriate location.

### Build and Run Desktop (JVM) Application

To build and run the development version of the desktop app, use the run configuration from the run widget
in your IDE’s toolbar or run it directly from the terminal:
- on macOS/Linux
  ```shell
  ./gradlew :composeApp:run
  ```
- on Windows
  ```shell
  .\gradlew.bat :composeApp:run
  ```

---

Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html)…
