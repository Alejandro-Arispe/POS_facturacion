param(
    [string]$Email = 'mococe5860@ofags.com',
    [switch]$Local
)

$ErrorActionPreference = 'Stop'
Push-Location $PSScriptRoot
try {
    $profiles = if ($Local) { 'local,seed-demo' } else { 'seed-demo' }
    & .\mvnw.cmd '-DskipTests' '-Dspring-boot.run.main-class=com.pos.common.seed.SeedDemoCommand' "-Dspring-boot.run.profiles=$profiles" "-Dspring-boot.run.arguments=--app.demo-seed.email=$Email" 'spring-boot:run'
    if ($LASTEXITCODE -ne 0) {
        throw "No se pudo completar el seeder (codigo $LASTEXITCODE)."
    }
}
finally {
    Pop-Location
}
