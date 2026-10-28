-- Preparación de esquema para instalaciones sin ddl-auto:update.
-- Ejecutar antes de iniciar la versión que contiene RbacMigration V3.
-- El servicio realiza la conversión de datos y registra V3; no insertar el marcador aquí.
ALTER TABLE cliente ADD COLUMN IF NOT EXISTS id_empresa INTEGER REFERENCES empresa(id_empresa);
CREATE INDEX IF NOT EXISTS ix_cliente_empresa_usuario ON cliente(id_empresa,id_usuario);
