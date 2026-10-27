-- Read-only post-migration diagnostics. Run after the application has applied V1-V3.
-- Report IDs only; never infer or grant a tenant/permission to resolve an ambiguity.
SELECT version, applied_at FROM rbac_migration ORDER BY version;

SELECT 'unmapped_permission' AS issue, id_permiso AS id
FROM permiso WHERE codigo_canonico IS NULL
   OR codigo_canonico !~ '^[a-z][a-z0-9_]*:[a-z][a-z0-9_]*(:[a-z][a-z0-9_]*)?$';

SELECT 'unscoped_client' AS issue, id_cliente AS id
FROM cliente WHERE id_empresa IS NULL;

SELECT 'cross_company_assignment' AS issue, ur.id_usuario_rol AS id
FROM usuario_rol ur JOIN rol r ON r.id_rol=ur.id_rol
LEFT JOIN sucursal s ON s.id_sucursal=ur.id_sucursal
WHERE (r.id_empresa IS NOT NULL AND r.id_empresa<>ur.id_empresa)
   OR (ur.id_sucursal IS NOT NULL AND (s.id_sucursal IS NULL OR s.id_empresa<>ur.id_empresa));

SELECT 'company_without_active_owner' AS issue, e.id_empresa AS id
FROM empresa e WHERE e.activo=true AND NOT EXISTS (
    SELECT 1 FROM usuario_rol ur JOIN rol r ON r.id_rol=ur.id_rol
    JOIN usuario u ON u.id_usuario=ur.id_usuario
    WHERE ur.id_empresa=e.id_empresa AND ur.activo=true AND u.activo=true
      AND r.activo=true AND r.id_empresa IS NULL AND r.protegido=true
      AND r.tipo='SISTEMA' AND r.nombre='ADMINISTRADOR'
);

SELECT 'invalid_role_scope' AS issue, id_rol AS id
FROM rol WHERE ambito IS DISTINCT FROM CASE WHEN id_empresa IS NULL THEN 'GLOBAL' ELSE 'EMPRESA:'||id_empresa END
   OR nombre_normalizado IS DISTINCT FROM upper(trim(nombre));
