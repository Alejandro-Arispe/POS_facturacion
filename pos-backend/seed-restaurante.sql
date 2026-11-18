-- 1. Asegurar segunda sucursal para la empresa 1
INSERT INTO sucursal (id_empresa, nombre, direccion, telefono, ciudad, fecha_registro, activo)
SELECT 1, 'Juanjos Sucursal Centro', 'Av. 16 de Julio #450, El Prado', '78912345', 'La Paz', CURRENT_DATE, true
WHERE NOT EXISTS (SELECT 1 FROM sucursal WHERE id_empresa = 1 AND nombre = 'Juanjos Sucursal Centro');

-- 2. Asegurar cajas para ambas sucursales
INSERT INTO caja (id_sucursal, codigo, nombre, fecha_creacion, activo)
SELECT s.id_sucursal, 'CAJA-01-' || s.id_sucursal, 'Caja Principal', CURRENT_DATE, true
FROM sucursal s
WHERE s.id_empresa = 1
  AND NOT EXISTS (SELECT 1 FROM caja c WHERE c.id_sucursal = s.id_sucursal);

-- 3. Crear personas y usuarios para los trabajadores (contrasena 123456 con hash bcrypt)
-- Sancho
INSERT INTO persona (nombre_completo, fecha_nacimiento, genero, telefono, documento)
SELECT 'Sancho Panza', '1995-05-10', 'M', '71000001', 'CI-SANCHO-1'
WHERE NOT EXISTS (SELECT 1 FROM usuario WHERE email = 'sancho.panza1@gmail.com');

INSERT INTO usuario (email, contrasena, activo, id_persona, fecha_creacion)
SELECT 'sancho.panza1@gmail.com', '$2a$10$FXtIwRXWsE6RaA4EbrdDoO4Bjz6pMKsTw53m6U/RRaVKCp0OHazem', true, p.id_persona, CURRENT_DATE
FROM persona p WHERE p.documento = 'CI-SANCHO-1'
  AND NOT EXISTS (SELECT 1 FROM usuario WHERE email = 'sancho.panza1@gmail.com');

-- Pedro
INSERT INTO persona (nombre_completo, fecha_nacimiento, genero, telefono, documento)
SELECT 'Pedro Picapiedra', '1998-08-20', 'M', '71000002', 'CI-PEDRO-2'
WHERE NOT EXISTS (SELECT 1 FROM usuario WHERE email = 'pedro.picapiedra1@gmail.com');

INSERT INTO usuario (email, contrasena, activo, id_persona, fecha_creacion)
SELECT 'pedro.picapiedra1@gmail.com', '$2a$10$FXtIwRXWsE6RaA4EbrdDoO4Bjz6pMKsTw53m6U/RRaVKCp0OHazem', true, p.id_persona, CURRENT_DATE
FROM persona p WHERE p.documento = 'CI-PEDRO-2'
  AND NOT EXISTS (SELECT 1 FROM usuario WHERE email = 'pedro.picapiedra1@gmail.com');

-- Martin
INSERT INTO persona (nombre_completo, fecha_nacimiento, genero, telefono, documento)
SELECT 'Martin Orellana', '2000-03-15', 'M', '71000003', 'CI-MARTIN-3'
WHERE NOT EXISTS (SELECT 1 FROM usuario WHERE email = 'martin.orellana1@gmail.com');

INSERT INTO usuario (email, contrasena, activo, id_persona, fecha_creacion)
SELECT 'martin.orellana1@gmail.com', '$2a$10$FXtIwRXWsE6RaA4EbrdDoO4Bjz6pMKsTw53m6U/RRaVKCp0OHazem', true, p.id_persona, CURRENT_DATE
FROM persona p WHERE p.documento = 'CI-MARTIN-3'
  AND NOT EXISTS (SELECT 1 FROM usuario WHERE email = 'martin.orellana1@gmail.com');

-- Asignar rol EMPLEADO a los trabajadores en la empresa 1
INSERT INTO usuario_rol (id_usuario, id_empresa, id_rol, id_sucursal, activo, fecha, sucursal_ambito)
SELECT u.id_usuario, 1, 3, (SELECT MIN(id_sucursal) FROM sucursal WHERE id_empresa = 1), true, CURRENT_DATE, (SELECT MIN(id_sucursal) FROM sucursal WHERE id_empresa = 1)
FROM usuario u
WHERE u.email IN ('sancho.panza1@gmail.com', 'pedro.picapiedra1@gmail.com', 'martin.orellana1@gmail.com')
  AND NOT EXISTS (SELECT 1 FROM usuario_rol ur WHERE ur.id_usuario = u.id_usuario AND ur.id_empresa = 1);

-- 4. Categorias del restaurante
INSERT INTO categoria_producto (id_empresa, nombre, descripcion, activo)
SELECT 1, 'Pollos', 'Variedades de pollo broaster y a la brasa', true
WHERE NOT EXISTS (SELECT 1 FROM categoria_producto WHERE id_empresa = 1 AND nombre = 'Pollos');

INSERT INTO categoria_producto (id_empresa, nombre, descripcion, activo)
SELECT 1, 'Hamburguesas', 'Hamburguesas artesanales de res y pollo', true
WHERE NOT EXISTS (SELECT 1 FROM categoria_producto WHERE id_empresa = 1 AND nombre = 'Hamburguesas');

INSERT INTO categoria_producto (id_empresa, nombre, descripcion, activo)
SELECT 1, 'Lomitos', 'Lomitos clasicos y especiales de carne de res', true
WHERE NOT EXISTS (SELECT 1 FROM categoria_producto WHERE id_empresa = 1 AND nombre = 'Lomitos');

INSERT INTO categoria_producto (id_empresa, nombre, descripcion, activo)
SELECT 1, 'Bebidas', 'Refrescos y gaseosas', true
WHERE NOT EXISTS (SELECT 1 FROM categoria_producto WHERE id_empresa = 1 AND nombre = 'Bebidas');

-- 5. Subcategorias
INSERT INTO subcategoria_producto (id_categoria_producto, nombre, descripcion, activo)
SELECT c.id_categoria_producto, 'Combos y Piezas', 'Combos individuales y familiares', true
FROM categoria_producto c WHERE c.id_empresa = 1 AND c.nombre = 'Pollos'
  AND NOT EXISTS (SELECT 1 FROM subcategoria_producto sc WHERE sc.id_categoria_producto = c.id_categoria_producto AND sc.nombre = 'Combos y Piezas');

INSERT INTO subcategoria_producto (id_categoria_producto, nombre, descripcion, activo)
SELECT c.id_categoria_producto, 'Hamburguesas Clasicas y Dobles', 'Linea de hamburguesas', true
FROM categoria_producto c WHERE c.id_empresa = 1 AND c.nombre = 'Hamburguesas'
  AND NOT EXISTS (SELECT 1 FROM subcategoria_producto sc WHERE sc.id_categoria_producto = c.id_categoria_producto AND sc.nombre = 'Hamburguesas Clasicas y Dobles');

INSERT INTO subcategoria_producto (id_categoria_producto, nombre, descripcion, activo)
SELECT c.id_categoria_producto, 'Lomitos Completos', 'Linea de lomitos al plato y sandwich', true
FROM categoria_producto c WHERE c.id_empresa = 1 AND c.nombre = 'Lomitos'
  AND NOT EXISTS (SELECT 1 FROM subcategoria_producto sc WHERE sc.id_categoria_producto = c.id_categoria_producto AND sc.nombre = 'Lomitos Completos');

INSERT INTO subcategoria_producto (id_categoria_producto, nombre, descripcion, activo)
SELECT c.id_categoria_producto, 'Gaseosas', 'Bebidas embotelladas', true
FROM categoria_producto c WHERE c.id_empresa = 1 AND c.nombre = 'Bebidas'
  AND NOT EXISTS (SELECT 1 FROM subcategoria_producto sc WHERE sc.id_categoria_producto = c.id_categoria_producto AND sc.nombre = 'Gaseosas');

-- 6. Productos del restaurante
INSERT INTO producto (id_empresa, id_subcategoria, nombre, codigo_barra, descripcion, unidad_medida, precio, costo, activo)
SELECT 1, (SELECT id_subcategoria FROM subcategoria_producto WHERE nombre = 'Combos y Piezas' LIMIT 1), '1/4 Pollo Broaster con Papas', '777001', 'Pierna o pechuga con papas fritas y salsas', 'Porcion', 25.00, 14.00, true
WHERE NOT EXISTS (SELECT 1 FROM producto WHERE id_empresa = 1 AND codigo_barra = '777001');

INSERT INTO producto (id_empresa, id_subcategoria, nombre, codigo_barra, descripcion, unidad_medida, precio, costo, activo)
SELECT 1, (SELECT id_subcategoria FROM subcategoria_producto WHERE nombre = 'Combos y Piezas' LIMIT 1), 'Combo Familiar 8 Piezas', '777002', '8 presas de pollo, porcion grande de papas y arroz', 'Combo', 85.00, 48.00, true
WHERE NOT EXISTS (SELECT 1 FROM producto WHERE id_empresa = 1 AND codigo_barra = '777002');

INSERT INTO producto (id_empresa, id_subcategoria, nombre, codigo_barra, descripcion, unidad_medida, precio, costo, activo)
SELECT 1, (SELECT id_subcategoria FROM subcategoria_producto WHERE nombre = 'Hamburguesas Clasicas y Dobles' LIMIT 1), 'Hamburguesa Clasica con Queso', '777003', 'Carne de res 150g, queso cheddar, lechuga y tomate', 'Unidad', 22.00, 11.50, true
WHERE NOT EXISTS (SELECT 1 FROM producto WHERE id_empresa = 1 AND codigo_barra = '777003');

INSERT INTO producto (id_empresa, id_subcategoria, nombre, codigo_barra, descripcion, unidad_medida, precio, costo, activo)
SELECT 1, (SELECT id_subcategoria FROM subcategoria_producto WHERE nombre = 'Hamburguesas Clasicas y Dobles' LIMIT 1), 'Hamburguesa Doble Carne y Tocineta', '777004', 'Doble carne de res 300g, tocino crocante, queso y salsas', 'Unidad', 32.00, 17.00, true
WHERE NOT EXISTS (SELECT 1 FROM producto WHERE id_empresa = 1 AND codigo_barra = '777004');

INSERT INTO producto (id_empresa, id_subcategoria, nombre, codigo_barra, descripcion, unidad_medida, precio, costo, activo)
SELECT 1, (SELECT id_subcategoria FROM subcategoria_producto WHERE nombre = 'Lomitos Completos' LIMIT 1), 'Lomito Completo Especial', '777005', 'Carne de lomo seleccionada, jamon, queso, huevo y papas al hilo', 'Sandwich', 28.00, 15.00, true
WHERE NOT EXISTS (SELECT 1 FROM producto WHERE id_empresa = 1 AND codigo_barra = '777005');

INSERT INTO producto (id_empresa, id_subcategoria, nombre, codigo_barra, descripcion, unidad_medida, precio, costo, activo)
SELECT 1, (SELECT id_subcategoria FROM subcategoria_producto WHERE nombre = 'Lomitos Completos' LIMIT 1), 'Lomito al Plato Ejecutivo', '777006', 'Bife de lomo a la plancha con huevo montado, arroz y papas fritas', 'Plato', 35.00, 19.00, true
WHERE NOT EXISTS (SELECT 1 FROM producto WHERE id_empresa = 1 AND codigo_barra = '777006');

INSERT INTO producto (id_empresa, id_subcategoria, nombre, codigo_barra, descripcion, unidad_medida, precio, costo, activo)
SELECT 1, (SELECT id_subcategoria FROM subcategoria_producto WHERE nombre = 'Gaseosas' LIMIT 1), 'Coca Cola 500ml', '777007', 'Bebida gaseosa personal en botella PET', 'Botella', 8.00, 4.50, true
WHERE NOT EXISTS (SELECT 1 FROM producto WHERE id_empresa = 1 AND codigo_barra = '777007');

-- 7. Asignar Stock en ambas sucursales
INSERT INTO stock (id_sucursal, id_producto, cantidad, stock_minimo, stock_maximo, fecha_actualizacion)
SELECT s.id_sucursal, p.id_producto, 50, 10, 200, NOW()
FROM sucursal s
CROSS JOIN producto p
WHERE s.id_empresa = 1 AND p.id_empresa = 1
  AND NOT EXISTS (SELECT 1 FROM stock st WHERE st.id_sucursal = s.id_sucursal AND st.id_producto = p.id_producto);

