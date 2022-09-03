# Compass SDK
Librería para la integración de medios digitales con la tecnología Compass de Marfeel.

## Características

- Tracking del tiempo de permanencia en una página
- Control del porcentaje de scroll
- Identificación de usuario
- Manejo de RFV
- Tracking de conversiones

## Instalación

TBD

## Inicialización

Antes de poder empezar a monitorizar las páginas vistas se debe inicializar el SDK llamando a la función `initialize` desde su clase Application

```kotlin
CompassTracking.initialize(context: Context, accountId: String)
```
El parámetro accountId es el identificador de cuenta provisto por Compass

## Uso

Todas las funcionalidades de la librería se realizan a través CompassTracking. Para obtener una instancia de CompassTracking llame a la funcion:

```kotlin
val tracker = CompassTracking.getInstance()
```

### Tracking de páginas

CompassTracker se encarga automáticamente de controlar el tiempo que el usuario se mantiene en una página. Para indicar que comience el tracking de una página concreta use la función startPageView, indicando la url de la página.

```kotlin
tracker.startPageView(url: String)
```

CompassTracker continuará registrando el tiempo de permanencia en la página hasta que se llame de nuevo a startPageView con una url diferente. O bien si el desarrollador lo indica mediante el método stopTracking()

```kotlin
tracker.stopTracking()
```

### Control del scroll

Si quiere que el sistema registre el porcentaje de scroll que el usuario ha hecho en la página, indique en la función startPageView el NestedScrollView en el que se está mostrando el contenido al usuario.

```kotlin
tracker.startPageView(url: String, scrollView: NestedScrollView)
```

En el caso de usar [Jetpack Compose](https://developer.android.com/jetpack/compose) se debe usar la función `startTracking(url: String)` junto con la función componible

```kotlin
CompassScrollTrackerEffect(scrollState: ScrollState)
```

### Identificación de usuario

Para asociar al usuario de la aplicación con los registros generados por la librería, utilice el método setUserId, indicando el identificador del usuario en su plataforma.

```kotlin
setUserId(userId: String)
```

Adicionalmente, puede indicar el tipo de usuario, actualmente la librería permite los tipos ANONYMOUS (para usuarios sin sesión), LOGGED (para usuarios registrados), PAID (para usuarios de pago) y CUSTOM. Para indicar el tipo de usuario use el método setUserType.

```kotlin
tracker.setUserType(UserType.Anonymous)

tracker.setUserType(UserType.Logged)

tracker.setUserType(UserType.Paid)

tracker.setUserType(UserType.Custom(9))
```
Si el tipo de usuario es *Custom* debe añadirse un identificador numérico al tipo (Los valores del 1 al 3 están reservados para el resto de tipos)

Es recomendable que indique el identificador y el tipo de usuario antes de realizar el primer tracking.

### Manejo de RFV

Si quiere obtener el código RFV de Compass utilice el método getRFV. Este método devuelve el código RFV en un callback.

```kotlin
tracker.getRFV { rfv ->
//Manejo del rfv recibido
}
```

Está disponible una versión síncrona de la misma función que debe ser llamada desde un hilo distinto al principal, por ejemplo usando corrutinas.


### Tracking de conversiones

Si quiere indicar una conversión, puede llamar en cualquier momento al método track(conversion: String).

```kotlin
tracker.track(conversion: Conversion)
```
