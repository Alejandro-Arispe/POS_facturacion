param([ValidateRange(55430,55439)][int]$Port = 55439)

$ErrorActionPreference = 'Stop'
$backend = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../pos-backend'))
$run = Join-Path $backend ('target/rbac8-drill-' + [guid]::NewGuid().ToString('N'))
$data = Join-Path $run 'data'
$previousJdbc = $env:RBAC_TEST_JDBC_URL
$started = $false

function Invoke-Checked([string]$Program, [string[]]$Arguments) {
    & $Program @Arguments
    if ($LASTEXITCODE -ne 0) { throw "$Program failed with exit code $LASTEXITCODE" }
}
function Sql([string]$Database, [string]$Statement) {
    $result = & psql -X -h 127.0.0.1 -p $Port -U rbac_test -d $Database -v ON_ERROR_STOP=1 -At -c $Statement
    if ($LASTEXITCODE -ne 0) { throw 'SQL validation failed.' }
    return ($result -join "`n").Trim()
}

try {
    foreach ($program in @('initdb','pg_ctl','psql','pg_dump','pg_restore')) {
        Get-Command $program -ErrorAction Stop | Out-Null
    }
    New-Item -ItemType Directory -Path $run | Out-Null
    # Fresh cluster, loopback only. No credentials or settings from application.yml/.env.
    Invoke-Checked 'initdb' @('-D',$data,'-U','rbac_test','-A','trust','--no-locale','-E','UTF8')
    # Windows shells may wait on inherited pipes kept open by postgres. Wait for pg_ctl
    # itself, with redirected handles, instead of waiting for its entire process tree.
    $control = Start-Process -FilePath (Get-Command pg_ctl).Source -ArgumentList @(
        '-D',('"' + $data + '"'),'-l',('"' + (Join-Path $run 'postgres.log') + '"'),
        '-o',('"-h 127.0.0.1 -p ' + $Port + '"'),'-w','start'
    ) -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $run 'start.log') -RedirectStandardError (Join-Path $run 'start-error.log')
    if (-not $control.WaitForExit(30000) -or $control.ExitCode -ne 0) { throw "PostgreSQL startup failed. See $run" }
    $started = $true
    foreach ($suite in @('schema','roles','products','operations','channels','migration','catalog','final','recovery')) {
        Sql 'postgres' "CREATE DATABASE rbac8_drill_$suite" | Out-Null
    }

    # Rehearse the preparation scripts on a minimal OLD schema, twice.
    Sql 'rbac8_drill_schema' @'
CREATE TABLE empresa(id_empresa INTEGER PRIMARY KEY);
CREATE TABLE sucursal(id_sucursal INTEGER PRIMARY KEY,id_empresa INTEGER REFERENCES empresa);
CREATE TABLE usuario(id_usuario INTEGER PRIMARY KEY);
CREATE TABLE permiso(id_permiso INTEGER PRIMARY KEY,codigo VARCHAR(100) NOT NULL);
CREATE TABLE rol(id_rol INTEGER PRIMARY KEY,id_empresa INTEGER,nombre VARCHAR(100));
CREATE TABLE rol_permiso(id_rol INTEGER,id_permiso INTEGER);
CREATE TABLE usuario_rol(id_usuario INTEGER,id_empresa INTEGER,id_rol INTEGER,id_sucursal INTEGER);
CREATE TABLE cliente(id_cliente INTEGER PRIMARY KEY,id_usuario INTEGER);
'@ | Out-Null
    foreach ($repeat in 1..2) {
        foreach ($file in @('V001__schema.sql','V003__client_scope.sql')) {
            Invoke-Checked 'psql' @('-X','-h','127.0.0.1','-p',"$Port",'-U','rbac_test','-d','rbac8_drill_schema','-v','ON_ERROR_STOP=1','-f',(Join-Path $backend "src/main/resources/db/rbac/postgresql/$file"))
        }
    }
    if ((Sql 'rbac8_drill_schema' "SELECT count(*) FROM information_schema.columns WHERE table_name='permiso' AND column_name='codigo_canonico'") -ne '1') { throw 'Legacy schema preparation failed.' }

    $env:RBAC_TEST_JDBC_URL = "jdbc:postgresql://127.0.0.1:$Port/rbac8_drill"
    Push-Location $backend
    try {
        $testLog = Join-Path $run 'tests.log'
        & .\mvnw.cmd -q '-Dtest=RbacTests,ProductFieldRbacTests,PosOperationsRbacTests,ReadChannelsRbacTests,RbacLegacyMigrationTests,CatalogUpgradeRbacTests,RbacFinalValidationTests' test *> $testLog
        if ($LASTEXITCODE -ne 0) { Get-Content $testLog -Tail 65; throw "PostgreSQL integration tests failed. See $testLog" }
        $testCount = 0
        foreach ($suite in @('com.pos.usuarios.RbacTests','com.pos.productos.ProductFieldRbacTests',
            'com.pos.operaciones.PosOperationsRbacTests','com.pos.operaciones.ReadChannelsRbacTests',
            'com.pos.usuarios.RbacLegacyMigrationTests','com.pos.usuarios.CatalogUpgradeRbacTests','com.pos.usuarios.RbacFinalValidationTests')) {
            $report = "target/surefire-reports/TEST-$suite.xml"
            [xml]$junit = Get-Content -LiteralPath $report
            $testCount += [int]$junit.testsuite.tests
            Copy-Item -LiteralPath $report -Destination $run
        }
    } finally { Pop-Location }

    # Physical backup of the migrated fixture, then restore to another EMPTY disposable database.
    $dump = Join-Path $run 'migrated-fixture.dump'
    Invoke-Checked 'pg_dump' @('-h','127.0.0.1','-p',"$Port",'-U','rbac_test','-d','rbac8_drill_final','-Fc','--no-owner','--no-acl','-f',$dump)
    Invoke-Checked 'pg_restore' @('-h','127.0.0.1','-p',"$Port",'-U','rbac_test','-d','rbac8_drill_recovery','--exit-on-error','--no-owner','--no-acl',$dump)
    Invoke-Checked 'psql' @('-X','-h','127.0.0.1','-p',"$Port",'-U','rbac_test','-d','rbac8_drill_recovery','-v','ON_ERROR_STOP=1',
        '-f',(Join-Path $backend 'src/main/resources/db/rbac/postgresql/verify_rbac.sql'),'-o',(Join-Path $run 'diagnostics.txt'))
    $fingerprintSql = @'
SELECT md5(concat_ws('|',
 (SELECT string_agg(concat_ws(':',id_rol,id_permiso,activo,puede_delegar),',' ORDER BY id_rol,id_permiso) FROM rol_permiso),
 (SELECT string_agg(concat_ws(':',id_rol,nombre,descripcion,activo,id_empresa),',' ORDER BY id_rol) FROM rol),
 (SELECT string_agg(concat_ws(':',id_usuario,id_empresa,id_rol,id_sucursal,activo),',' ORDER BY id_usuario_rol) FROM usuario_rol),
 (SELECT string_agg(concat_ws(':',id_permiso,codigo,codigo_canonico,activo),',' ORDER BY id_permiso) FROM permiso),
 (SELECT string_agg(version::text,',' ORDER BY version) FROM rbac_migration)));
'@
    $before = Sql 'rbac8_drill_final' $fingerprintSql
    $after = Sql 'rbac8_drill_recovery' $fingerprintSql
    if ($before -ne $after) { throw 'Recovery changed roles, assignments, grants or migration markers.' }
    if ((Sql 'rbac8_drill_recovery' 'SELECT string_agg(version::text,'','' ORDER BY version) FROM rbac_migration') -ne '1,2,3') { throw 'Missing migration markers.' }
    [ordered]@{
        database = 'PostgreSQL'; port = $Port; legacySchemaRepeated = $true
        integrationTests = 'passed'; tests = $testCount; restoredFingerprintMatches = ($before -eq $after)
        migrationVersions = '1,2,3'; fingerprint = $before; artifactDirectory = $run
    } | ConvertTo-Json | Set-Content -Encoding utf8 (Join-Path $run 'result.json')
    Write-Output "RBAC PostgreSQL validation passed. Evidence: $run"
} finally {
    $env:RBAC_TEST_JDBC_URL = $previousJdbc
    if ($started) { & pg_ctl -D $data -m fast -w stop }
    # Keep evidence and the stopped fixture; never delete or touch the user's database.
}
