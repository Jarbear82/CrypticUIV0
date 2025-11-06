# Nexus Note

A visual, schema-driven, and structured notetaking application.

Nexus Note helps you build a knowledge graph by enforcing a structured approach. Instead of just writing notes, you define *types* of notes (Schemas) and link them together. This creates a centralized, queryable, and visual database of your ideas.

## Core Features

* **Schema-First Design:** Define your data structure upfront. Create "Person," "Location," or "Project" schemas with custom properties. This prevents note duplication and ensures consistency.
* **Force-Directed Graph:** Automatically visualizes your notes as a graph. See the connections between your ideas at a glance.
* **Data Management:** Full create, read, update, and delete (CRUD) operations for your Schemas, Nodes (notes), and Edges (links).
* **Local-First:** All data is stored locally in a `.sqlite` file, giving you full control.

## Intended Audiences

* **Writers & World-Builders:** Keep track of characters, locations, and timelines.
* **Students & Researchers:** Link concepts, papers, and authors.
* **Anyone** looking for a way to visualize connected data in a graph.

## Technology Stack

This is a Kotlin Multiplatform project built with modern tools:

* **Kotlin Multiplatform:** Shared logic written 100% in Kotlin.
* **Compose Multiplatform:** A single, declarative UI for Desktop (and soon, more).
* **SQLDelight:** Generates type-safe Kotlin APIs from your SQL database schema.
* **ViewModel/Repository Pattern:** A clean and scalable architecture.

## Build and Run (Desktop)

To build and run the development version of the desktop app:

* **on macOS/Linux:**
  ```shell
  ./gradlew :composeApp:run
  ```
* **on Windows:**
  ```shell
  .\gradlew.bat :composeApp:run
  ```

## Roadmap (Feature Wishlist)

This project is in active development. Here's a look at what's planned:

#### Core Usability

* Search and filtering in list views
* Schema-based graph filtering
* Graph UI controls (zoom buttons, physics settings)
* A "Settings" page with Light/Dark theme support

#### Feature Expansion

* Import/Export data (JSON, SQL)
* Take a screenshot of the graph
* **Document to Graph**
    - Preview graph (for imports)
    - Parse Document to Graph
        - `.docx`
        - `.odt`
    - Parse Markdown to Graph
        - [Obsidian Flavor](https://help.obsidian.md/obsidian-flavored-markdown)
        - [CommonMark Flavor](https://spec.commonmark.org/)
        - [Github Flavor](https://github.github.com/gfm/)
    - Parse PDF to Graph
    - Parse AsciiDoc to Graph

#### Long-Term

* Android & iOS support
* Data synchronization (Cloud or P2P)
* Advanced query support (Cypher)