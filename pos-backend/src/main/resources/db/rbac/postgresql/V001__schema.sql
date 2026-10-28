-- Optional pre-deployment schema preparation for installations using ddl-auto=validate.
-- Data conversion and indexes are applied by RbacMigration, version 1, after CatalogSeedRunner.
-- Do not insert a migration marker manually.
BEGIN;
ALTER TABLE permiso ADD COLUMN IF NOT EXISTS codigo_canonico VARCHAR(150);
ALTER TABLE permiso ADD COLUMN IF NOT EXISTS recurso VARCHAR(60);
ALTER TABLE permiso ADD COLUMN IF NOT EXISTS accion VARCHAR(50);
ALTER TABLE permiso ADD COLUMN IF NOT EXISTS atributo VARCHAR(60);
ALTER TABLE permiso ADD COLUMN IF NOT EXISTS descripcion VARCHAR(255);
ALTER TABLE permiso ADD COLUMN IF NOT EXISTS activo BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE rol ADD COLUMN IF NOT EXISTS nombre_normalizado VARCHAR(100);
ALTER TABLE rol ADD COLUMN IF NOT EXISTS ambito VARCHAR(40);
ALTER TABLE rol ADD COLUMN IF NOT EXISTS protegido BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE rol_permiso ADD COLUMN IF NOT EXISTS puede_delegar BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE usuario_rol ADD COLUMN IF NOT EXISTS sucursal_ambito INTEGER;
CREATE UNIQUE INDEX IF NOT EXISTS ux_rbac_permission_code ON permiso(codigo_canonico);
CREATE TABLE IF NOT EXISTS rbac_migration(version INTEGER PRIMARY KEY, applied_at TIMESTAMP NOT NULL);
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname='fk_rbac_role_company') THEN
    ALTER TABLE rol ADD CONSTRAINT fk_rbac_role_company FOREIGN KEY(id_empresa) REFERENCES empresa(id_empresa);
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname='fk_rbac_assignment_company') THEN
    ALTER TABLE usuario_rol ADD CONSTRAINT fk_rbac_assignment_company FOREIGN KEY(id_empresa) REFERENCES empresa(id_empresa);
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname='fk_rbac_assignment_branch') THEN
    ALTER TABLE usuario_rol ADD CONSTRAINT fk_rbac_assignment_branch FOREIGN KEY(id_sucursal) REFERENCES sucursal(id_sucursal);
  END IF;
END $$;
CREATE TABLE IF NOT EXISTS invitacion_rol (
    id VARCHAR(36) PRIMARY KEY, id_empresa INTEGER NOT NULL REFERENCES empresa(id_empresa),
    id_usuario INTEGER NOT NULL REFERENCES usuario(id_usuario), id_emisor INTEGER NOT NULL REFERENCES usuario(id_usuario),
    id_rol INTEGER NOT NULL REFERENCES rol(id_rol), expira_en TIMESTAMP WITH TIME ZONE NOT NULL,
    consumida BOOLEAN NOT NULL DEFAULT FALSE, revocada BOOLEAN NOT NULL DEFAULT FALSE
);
COMMIT;
