# Plugin Definition and Development

This page describes how to define extension points, implement plugins, package them, and call them from the host application.

The current recommended split is:

1. contract layer
2. implementation layer

## Contract Layer

Shared extension contracts should live in:

- `geelato-plugin-all`

This layer normally contains:

- `PluginExtensionPoint`
- business extension interfaces
- shared DTOs and metadata classes

## Implementation Layer

Concrete plugin modules live in projects such as:

- `geelato-example-plugin`
- `geelato-ocr-plugin`

They typically contain:

- plugin main class
- plugin Spring configuration
- extension implementation classes
- `plugin.properties`

## Main Steps

### 1. Define an Extension Interface

Example:

```java
public interface Greeting extends PluginExtensionPoint {
    String getGreeting();
}
```

### 2. Implement the Plugin Main Class

The current example uses `SpringPlugin` and creates its own Spring `ApplicationContext`.

### 3. Add Plugin Spring Configuration

Example plugin configuration provides plugin-local beans such as `GreetingService`.

### 4. Implement the Extension

Add `@Extension` to the concrete class so PF4J can discover it.

### 5. Provide `plugin.properties`

The current example contains:

- `plugin.class`
- `plugin.dependencies`
- `plugin.id`
- `plugin.provider`
- `plugin.version`

### 6. Package the Plugin

The plugin module should:

- depend on `plugin-all`
- enable `org.pf4j.processor.ExtensionAnnotationProcessor`
- write plugin metadata into the jar manifest

After packaging, the jar should contain:

- `META-INF/extensions.idx`
- `META-INF/MANIFEST.MF`
- `plugin.properties`

## Host-Side Invocation

The host application should use:

- `PluginBeanProvider`

Example:

```java
Greeting greeting = pluginBeanProvider.getBean(Greeting.class, "example-plugin");
String result = greeting.getGreeting();
```

If no matching extension is found, the runtime throws:

- `UnFoundPluginException`

## Suggested Reading

- [Overview](overview.md)
- [Loading, Start/Stop and Uninstall](lifecycle.md)
- [Plugin Repository](repository.md)
