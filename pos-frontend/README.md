
Aplicacion frontend desarrollada con Angular para gestionar autenticacion, landing page y funcionalidades relacionadas con workshops.

## Estructura del proyecto

```text
.
??? angular.json
??? package.json
??? public/
??? src/
	??? index.html
	??? main.ts
	??? styles.css
	??? app/
		??? app.config.ts
		??? app.css
		??? app.html
		??? app.routes.ts
		??? app.spec.ts
		??? app.ts
		??? core/
		?   ??? services/
		??? features/
		?   ??? auth/
		?   ??? landing/
		?   ??? workshops/
		??? shared/
			??? components/
```

## Que hace cada carpeta

### `public/`
Contiene archivos estaticos que se sirven directamente, como iconos, imagenes o recursos publicos que no necesitan pasar por el compilador de Angular.

### `src/`
Es la carpeta principal de codigo fuente de la aplicacion.

### `src/index.html`
Punto de entrada HTML de la aplicacion. Angular monta aqui todo el contenido de la app.

### `src/main.ts`
Archivo que arranca la aplicacion Angular y conecta la configuracion principal con el componente raiz.

### `src/styles.css`
Estilos globales que se aplican en toda la aplicacion.

### `src/app/`
Contiene la estructura principal de la aplicacion Angular.

### `src/app/app.config.ts`
Archivo de configuracion general de la app, donde se definen proveedores, dependencias o ajustes globales.

### `src/app/app.css`
Estilos asociados al componente principal de la aplicacion.

### `src/app/app.html`
Plantilla HTML del componente principal.

### `src/app/app.routes.ts`
Definicion de rutas de navegacion entre paginas y vistas de la aplicacion.

### `src/app/app.spec.ts`
Pruebas unitarias del componente principal.

### `src/app/app.ts`
Componente raiz de la aplicacion. Normalmente actua como contenedor principal de la interfaz.

### `src/app/core/`
Contiene la base comun de la aplicacion. Aqui va lo que se usa en varias partes del proyecto y no pertenece a una pantalla en particular.

### `src/app/core/services/`
Servicios reutilizables, por ejemplo para consumir APIs, manejar autenticacion, guardar datos compartidos o centralizar llamadas comunes.

### `src/app/features/`
Agrupa las funcionalidades principales del proyecto por caso de uso o modulo, para mantener el codigo ordenado por partes funcionales.

### `src/app/features/auth/`
Carpeta de funcionalidades relacionadas con autenticacion y acceso de usuarios.

### `src/app/features/landing/`
Contiene las vistas publicas o iniciales de la aplicacion, como la pagina principal.

### `src/app/features/workshops/`
Agrupa todo lo relacionado con la gestion de workshops, incluyendo sus vistas y logica de negocio.

### `src/app/features/.../pages/`
En general, dentro de cada feature se guardan las paginas o vistas que pertenecen a esa funcionalidad.

### `src/app/shared/`
Elementos reutilizables en distintas partes de la aplicacion, sin depender de una feature concreta.

### `src/app/shared/components/`
Componentes visuales compartidos, como navbar, botones, modales o tarjetas.

### `src/app/shared/components/navbar/`
Componente de barra de navegacion principal.

## Scripts utiles

Instalar dependencias:

```bash
npm install
```

Iniciar el servidor de desarrollo:

```bash
npm start
```

Compilar el proyecto:

```bash
npm run build
```

Ejecutar pruebas:

```bash
npm test
```
