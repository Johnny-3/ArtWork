# ArtWork MVC Example

This project is a simple Java Swing application organized using the MVC pattern. It provides:

- A top left control panel with 12 color buttons, an eraser tool and sliders for line thickness and erase radius.
- A top right drawing canvas where you can drag to draw lines with the selected color and thickness or erase with the eraser tool.
- A bottom panel with a placeholder **Review** button.
- A dark mode toggle that inverts colors on the canvas.
- The control panel and canvas are separated by a resizable divider.

## Building and running

Ensure you have Maven installed. To run the application:

```bash
mvn clean compile exec:java
```

The main class is `com.artwork.mvc.Main`.

## UML

See `diagram.puml` for a PlantUML description of the project structure.
