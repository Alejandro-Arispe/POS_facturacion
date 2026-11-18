# Smoke E2E contra API local (perfil H2). No escribe archivos de secretos.
$ErrorActionPreference = 'Stop'
$Base = 'http://localhost:8000'
$AdminEmail = 'e2e.demo@example.com'
$CashierEmail = 'cajero.centro@example.com'
$Pass = 'Tienda2026!'
$Results = [System.Collections.Generic.List[object]]::new()

function Add-Result([string]$Flow, [string]$Step, [bool]$Ok, [string]$Detail = '') {
    $Results.Add([pscustomobject]@{ Flow = $Flow; Step = $Step; Ok = $Ok; Detail = $Detail })
    $mark = if ($Ok) { 'PASS' } else { 'FAIL' }
    Write-Host "[$mark] $Flow :: $Step$(if ($Detail) { " — $Detail" })"
}

function Invoke-Api {
    param(
        [string]$Method = 'GET',
        [string]$Path,
        [object]$Body = $null,
        [string]$Token = $null,
        [int[]]$ExpectStatus = @(200)
    )
    $headers = @{ Accept = 'application/json' }
    if ($Token) { $headers['Authorization'] = "Bearer $Token" }
    $uri = "$Base$Path"
    $params = @{
        Uri             = $uri
        Method          = $Method
        Headers         = $headers
        UseBasicParsing = $true
    }
    if ($null -ne $Body) {
        $params.ContentType = 'application/json; charset=utf-8'
        # ConvertTo-Json en arrays de 1 elemento degrada a objeto; forzamos array cuando aplica.
        if ($Body -is [System.Array]) {
            $params.Body = ConvertTo-Json -InputObject @($Body) -Depth 12 -Compress
        } else {
            $params.Body = ($Body | ConvertTo-Json -Depth 12 -Compress)
        }
    }
    try {
        $resp = Invoke-WebRequest @params
        $code = [int]$resp.StatusCode
        $json = $null
        if ($resp.Content) {
            try { $json = $resp.Content | ConvertFrom-Json } catch { $json = $resp.Content }
        }
        return @{ Ok = ($ExpectStatus -contains $code); Status = $code; Data = $json; Raw = $resp.Content; Error = $null }
    }
    catch {
        $ex = $_.Exception
        $code = 0
        $raw = $null
        if ($ex.Response) {
            $code = [int]$ex.Response.StatusCode
            try {
                $reader = [System.IO.StreamReader]::new($ex.Response.GetResponseStream())
                $raw = $reader.ReadToEnd()
                $reader.Dispose()
            } catch {}
        }
        $data = $null
        if ($raw) { try { $data = $raw | ConvertFrom-Json } catch { $data = $raw } }
        return @{ Ok = ($ExpectStatus -contains $code); Status = $code; Data = $data; Raw = $raw; Error = $ex.Message }
    }
}

function DetailOf($r) {
    if ($r.Data -and $r.Data.detail) { return [string]$r.Data.detail }
    if ($r.Error) { return $r.Error }
    if ($r.Raw) { return ($r.Raw.Substring(0, [Math]::Min(180, $r.Raw.Length))) }
    return "HTTP $($r.Status)"
}

# --- Health ---
$h = Invoke-Api -Path '/'
Add-Result 'Infra' 'GET / health' $h.Ok ($(if ($h.Ok) { 'status=ok' } else { DetailOf $h }))

# --- Planes públicos ---
$planes = Invoke-Api -Path '/api/planes'
$planesOk = $planes.Ok -and @($planes.Data).Count -ge 1
Add-Result 'Catalogos' 'GET /api/planes' $planesOk ($(if ($planesOk) { "$($planes.Data.Count) planes" } else { DetailOf $planes }))

# --- Registro nuevo usuario ---
$stamp = Get-Date -Format 'yyyyMMddHHmmss'
$newEmail = "e2e.reg.$stamp@example.com"
$reg = Invoke-Api -Method POST -Path '/api/auth/register' -Body @{
    email            = $newEmail
    contrasena       = 'Test1234'
    nombre           = 'E2E'
    apellido         = 'Registro'
    fecha_nacimiento = '1995-05-15'
    genero           = 'O'
    telefono         = '70000001'
    documento        = "E2E$stamp"
}
Add-Result 'Auth' 'POST /api/auth/register' $reg.Ok ($(if ($reg.Ok) { "activo=$($reg.Data.activo)" } else { DetailOf $reg }))

$loginNew = Invoke-Api -Method POST -Path '/api/auth/login' -Body @{ email = $newEmail; contrasena = 'Test1234' }
Add-Result 'Auth' 'Login usuario recién registrado' $loginNew.Ok ($(if ($loginNew.Ok) { 'token ok' } else { DetailOf $loginNew }))

# --- Login admin demo ---
$login = Invoke-Api -Method POST -Path '/api/auth/login' -Body @{ email = $AdminEmail; contrasena = $Pass }
$token = $null
if ($login.Ok) {
    $token = $login.Data.access_token
    if (-not $token) { $token = $login.Data.accessToken }
}
Add-Result 'Auth' 'Login admin demo' ($null -ne $token) ($(if ($token) { 'Bearer ok' } else { DetailOf $login }))

if (-not $token) {
    Write-Host "`nAbortado: sin token admin." -ForegroundColor Red
    $Results | Format-Table -AutoSize
    exit 1
}

# --- Empresas / permisos ---
$empresas = Invoke-Api -Path '/api/empresas/mis-empresas' -Token $token
$empOk = $empresas.Ok -and @($empresas.Data).Count -ge 1
$idEmpresa = $null
if ($empOk) {
    $e0 = @($empresas.Data)[0]
    $idEmpresa = $e0.id_empresa
    if (-not $idEmpresa) { $idEmpresa = $e0.idEmpresa }
}
Add-Result 'Empresa' 'GET mis-empresas' $empOk ($(if ($empOk) { "id=$idEmpresa nombre=$($e0.nombre)" } else { DetailOf $empresas }))

$perm = Invoke-Api -Path "/api/empresas/$idEmpresa/mis-permisos" -Token $token
$permOk = $perm.Ok -and $perm.Data.permisos
Add-Result 'Empresa' 'GET mis-permisos' $permOk ($(if ($permOk) { "$($perm.Data.permisos.Count) permisos" } else { DetailOf $perm }))

$plan = Invoke-Api -Path "/api/empresas/$idEmpresa/mi-plan" -Token $token
Add-Result 'Empresa' 'GET mi-plan' $plan.Ok ($(if ($plan.Ok) { "estado=$($plan.Data.estado) plan=$($plan.Data.plan_nombre)" } else { DetailOf $plan }))

# --- Sucursales / cajas ---
$sucs = Invoke-Api -Path "/api/empresas/$idEmpresa/sucursales" -Token $token
$sucOk = $sucs.Ok -and @($sucs.Data).Count -ge 1
$idSucursal = $null
if ($sucOk) {
    $s0 = @($sucs.Data)[0]
    $idSucursal = $s0.id_sucursal
    if (-not $idSucursal) { $idSucursal = $s0.idSucursal }
}
Add-Result 'Sucursales' 'GET sucursales' $sucOk ($(if ($sucOk) { "count=$($sucs.Data.Count) first=$idSucursal" } else { DetailOf $sucs }))

$cajas = Invoke-Api -Path "/api/empresas/$idEmpresa/sucursales/$idSucursal/cajas" -Token $token
$cajaOk = $cajas.Ok -and @($cajas.Data).Count -ge 1
$idCaja = $null
if ($cajaOk) {
    $c0 = @($cajas.Data)[0]
    $idCaja = $c0.id_caja
    if (-not $idCaja) { $idCaja = $c0.idCaja }
}
Add-Result 'Caja' 'GET cajas' $cajaOk ($(if ($cajaOk) { "count=$($cajas.Data.Count) first=$idCaja" } else { DetailOf $cajas }))

# --- Productos / categorias / clientes / inventario ---
$prods = Invoke-Api -Path "/api/empresas/$idEmpresa/productos" -Token $token
$prodOk = $prods.Ok -and @($prods.Data).Count -ge 1
$idProducto = $null
$precio = $null
if ($prodOk) {
    $p0 = @($prods.Data) | Where-Object {
        $st = $_.stock
        if ($null -eq $st) { $st = $_.cantidad }
        ($null -eq $st) -or ([decimal]$st -gt 0)
    } | Select-Object -First 1
    if (-not $p0) { $p0 = @($prods.Data)[0] }
    $idProducto = $p0.id_producto
    if (-not $idProducto) { $idProducto = $p0.idProducto }
    $precio = $p0.precio_venta
    if (-not $precio) { $precio = $p0.precioVenta }
    if (-not $precio) { $precio = $p0.precio }
}
Add-Result 'Catalogo' 'GET productos' $prodOk ($(if ($prodOk) { "count=$($prods.Data.Count) prod=$idProducto precio=$precio" } else { DetailOf $prods }))

$cats = Invoke-Api -Path "/api/empresas/$idEmpresa/categorias-con-subcategorias" -Token $token
Add-Result 'Catalogo' 'GET categorias-con-subcategorias' $cats.Ok ($(if ($cats.Ok) { "count=$(@($cats.Data).Count)" } else { DetailOf $cats }))

$clientes = Invoke-Api -Path "/api/empresas/$idEmpresa/clientes" -Token $token
$cliOk = $clientes.Ok -and @($clientes.Data).Count -ge 1
$idCliente = $null
if ($cliOk) {
    $cl0 = @($clientes.Data)[0]
    $idCliente = $cl0.id_cliente
    if (-not $idCliente) { $idCliente = $cl0.idCliente }
    if (-not $idCliente) { $idCliente = $cl0.id }
    if (-not $idCliente -and $cl0.cliente) {
        $idCliente = $cl0.cliente.id_cliente
        if (-not $idCliente) { $idCliente = $cl0.cliente.idCliente }
    }
}
Add-Result 'Clientes' 'GET clientes empresa' $cliOk ($(if ($cliOk) { "count=$($clientes.Data.Count) first=$idCliente keys=$($cl0.PSObject.Properties.Name -join ',')" } else { DetailOf $clientes }))

$stock = Invoke-Api -Path "/api/inventario/empresas/$idEmpresa/sucursales/$idSucursal/stock" -Token $token
Add-Result 'Inventario' 'GET stock sucursal' $stock.Ok ($(if ($stock.Ok) { "items=$(@($stock.Data).Count)" } else { DetailOf $stock }))

$catCli = Invoke-Api -Path "/api/empresas/$idEmpresa/categorias-cliente" -Token $token
Add-Result 'Clientes' 'GET categorias-cliente' $catCli.Ok ($(if ($catCli.Ok) { "count=$(@($catCli.Data).Count)" } else { DetailOf $catCli }))

# --- Login cajero y apertura de caja + venta ---
$loginC = Invoke-Api -Method POST -Path '/api/auth/login' -Body @{ email = $CashierEmail; contrasena = $Pass }
$tokenC = $null
if ($loginC.Ok) {
    $tokenC = $loginC.Data.access_token
    if (-not $tokenC) { $tokenC = $loginC.Data.accessToken }
}
Add-Result 'Auth' 'Login cajero centro' ($null -ne $tokenC) ($(if ($tokenC) { 'Bearer ok' } else { DetailOf $loginC }))

$tipos = Invoke-Api -Path '/api/ventas/tipos-venta' -Token $tokenC
$metodos = Invoke-Api -Path '/api/ventas/metodos-pago' -Token $tokenC
$idTipoContado = $null
$idMetodoEfectivo = $null
if ($tipos.Ok) {
    $tv = @($tipos.Data) | Where-Object { $_.nombre -match 'Contado|contado' } | Select-Object -First 1
    if (-not $tv) { $tv = @($tipos.Data)[0] }
    $idTipoContado = $tv.id
    if (-not $idTipoContado) { $idTipoContado = $tv.id_tipo_venta }
    if (-not $idTipoContado) { $idTipoContado = $tv.idTipoVenta }
}
if ($metodos.Ok) {
    $mp = @($metodos.Data) | Where-Object { $_.nombre -match 'EFECTIVO|Efectivo' } | Select-Object -First 1
    if (-not $mp) { $mp = @($metodos.Data)[0] }
    $idMetodoEfectivo = $mp.id
    if (-not $idMetodoEfectivo) { $idMetodoEfectivo = $mp.id_metodo_pago }
    if (-not $idMetodoEfectivo) { $idMetodoEfectivo = $mp.idMetodoPago }
}
Add-Result 'Ventas' 'Catalogos tipo/metodo' ($tipos.Ok -and $metodos.Ok) ("tipo=$idTipoContado metodo=$idMetodoEfectivo")

# Resolver caja del cajero (puede ser otra sucursal/caja)
$empC = Invoke-Api -Path '/api/empresas/mis-empresas-empleado' -Token $tokenC
if (-not $empC.Ok -or @($empC.Data).Count -eq 0) {
    $empC = Invoke-Api -Path '/api/empresas/mis-empresas' -Token $tokenC
}
$idEmpC = $idEmpresa
$idSucC = $idSucursal
$idCajaC = $idCaja
if ($empC.Ok -and @($empC.Data).Count -ge 1) {
    $ec = @($empC.Data)[0]
    $tmp = $ec.id_empresa; if (-not $tmp) { $tmp = $ec.idEmpresa }
    if ($tmp) { $idEmpC = $tmp }
}
$sucC = Invoke-Api -Path "/api/empresas/$idEmpC/sucursales" -Token $tokenC
if ($sucC.Ok -and @($sucC.Data).Count -ge 1) {
    # Preferir Sucursal Centro
    $sc = @($sucC.Data) | Where-Object { $_.nombre -match 'Centro' } | Select-Object -First 1
    if (-not $sc) { $sc = @($sucC.Data)[0] }
    $idSucC = $sc.id_sucursal; if (-not $idSucC) { $idSucC = $sc.idSucursal }
    $cj = Invoke-Api -Path "/api/empresas/$idEmpC/sucursales/$idSucC/cajas" -Token $tokenC
    if ($cj.Ok -and @($cj.Data).Count -ge 1) {
        $idCajaC = @($cj.Data)[0].id_caja
        if (-not $idCajaC) { $idCajaC = @($cj.Data)[0].idCaja }
    }
}

$apertura = Invoke-Api -Method POST -Path "/api/cajas/$idCajaC/sesiones" -Token $tokenC -Body @{
    monto_inicial = 200
    nota          = 'E2E apertura'
}
$idSesion = $null
if ($apertura.Ok) {
    $idSesion = $apertura.Data.id_caja_sesion
    if (-not $idSesion) { $idSesion = $apertura.Data.idCajaSesion }
}
Add-Result 'Caja' 'POST abrir sesion' ($null -ne $idSesion) ($(if ($idSesion) { "sesion=$idSesion caja=$idCajaC" } else { DetailOf $apertura }))

# Stock del producto en la sucursal del cajero
$stockC = Invoke-Api -Path "/api/inventario/empresas/$idEmpC/sucursales/$idSucC/stock" -Token $tokenC
if ($stockC.Ok) {
    $item = @($stockC.Data) | Where-Object {
        $prodId = $_.id_producto; if (-not $prodId) { $prodId = $_.idProducto }
        $cant = $_.cantidad; if ($null -eq $cant) { $cant = $_.stock }
        ($prodId -eq $idProducto) -and ([decimal]$cant -gt 0)
    } | Select-Object -First 1
    if (-not $item) {
        $item = @($stockC.Data) | Where-Object {
            $cant = $_.cantidad; if ($null -eq $cant) { $cant = $_.stock }
            [decimal]$cant -gt 0
        } | Select-Object -First 1
        if ($item) {
            $idProducto = $item.id_producto; if (-not $idProducto) { $idProducto = $item.idProducto }
            if ($item.precio_venta) { $precio = $item.precio_venta }
            elseif ($item.precioVenta) { $precio = $item.precioVenta }
            elseif ($item.precio) { $precio = $item.precio }
        }
    }
}
if (-not $precio) { $precio = 10 }

$ventaOk = $false
$ventaDetail = 'skipped'
if ($idSesion -and $idProducto -and $idTipoContado -and $idMetodoEfectivo) {
    $unit = [decimal]$precio
    $ventaBody = @{
        id_tipo_venta    = [int]$idTipoContado
        id_cliente       = $null
        id_metodo_pago   = [int]$idMetodoEfectivo
        factura_linea    = $false
        subtotal         = $unit
        descuento_total  = 0
        total            = $unit
        estado           = 'pagada'
        pagos            = @(@{ id_metodo_pago = [int]$idMetodoEfectivo; monto = $unit })
        detalles         = @(@{
                id_producto     = [int]$idProducto
                cantidad        = 1
                precio_unitario = $unit
                descuento       = 0
                subtotal        = $unit
                total           = $unit
            })
    }
    $venta = Invoke-Api -Method POST -Path "/api/ventas/sesiones/$idSesion/ventas" -Token $tokenC -Body $ventaBody
    $ventaOk = $venta.Ok
    if ($ventaOk) {
        $vid = $venta.Data.id_venta; if (-not $vid) { $vid = $venta.Data.idVenta }
        $ventaDetail = "venta=$vid total=$($venta.Data.total)"
    } else {
        $ventaDetail = DetailOf $venta
    }
}
Add-Result 'Ventas' 'POST crear venta contado' $ventaOk $ventaDetail

$hist = if ($idSesion) { Invoke-Api -Path "/api/ventas/sesiones/$idSesion/ventas" -Token $tokenC } else { @{ Ok = $false; Status = 0; Data = $null; Error = 'sin sesion' } }
Add-Result 'Ventas' 'GET historial sesion' $hist.Ok ($(if ($hist.Ok) { "count=$(@($hist.Data).Count)" } else { DetailOf $hist }))

# Cierre de caja (monto esperado/real/diferencia)
$cierreOk = $false
$cierreDetail = 'skipped'
if ($idSesion -and $idMetodoEfectivo) {
    $esperado = [decimal]200
    if ($ventaOk) { $esperado = 200 + [decimal]$precio }
    $cierre = Invoke-Api -Method POST -Path "/api/cajas/sesiones/$idSesion/cierres" -Token $tokenC -Body @(
        @{
            id_metodo_pago = [int]$idMetodoEfectivo
            monto_esperado = $esperado
            monto_real     = $esperado
            diferencia     = 0
            observacion    = 'E2E cierre'
        }
    )
    $cierreOk = $cierre.Ok
    $cierreDetail = if ($cierreOk) { 'cerrada' } else { DetailOf $cierre }
}
Add-Result 'Caja' 'POST cerrar sesion' $cierreOk $cierreDetail

# --- Reportes (admin) ---
$desde = (Get-Date).AddDays(-35).ToString('yyyy-MM-dd')
$hasta = (Get-Date).ToString('yyyy-MM-dd')
$rep = Invoke-Api -Path "/api/reportes/$idEmpresa/resumenventas?desde=$desde&hasta=$hasta" -Token $token
Add-Result 'Reportes' 'GET resumenventas' $rep.Ok ($(if ($rep.Ok) { 'ok' } else { DetailOf $rep }))

$invRep = Invoke-Api -Path "/api/reportes/$idEmpresa/estadoinventario" -Token $token
Add-Result 'Reportes' 'GET estadoinventario' $invRep.Ok ($(if ($invRep.Ok) { 'ok' } else { DetailOf $invRep }))

$plantillas = Invoke-Api -Path '/api/reportes/plantillas' -Token $token
Add-Result 'Reportes' 'GET plantillas' $plantillas.Ok ($(if ($plantillas.Ok) { "count=$(@($plantillas.Data).Count)" } else { DetailOf $plantillas }))

$staff = Invoke-Api -Path "/api/empresas/$idEmpresa/personal" -Token $token
Add-Result 'Usuarios' 'GET personal' $staff.Ok ($(if ($staff.Ok) { 'ok' } else { DetailOf $staff }))

$roles = Invoke-Api -Path "/api/roles/empresa/$idEmpresa" -Token $token
Add-Result 'Usuarios' 'GET roles' $roles.Ok ($(if ($roles.Ok) { "count=$(@($roles.Data).Count)" } else { DetailOf $roles }))

$audit = Invoke-Api -Path "/api/empresas/$idEmpresa/bitacora" -Token $token
if (-not $audit.Ok) {
    $audit = Invoke-Api -Path "/api/bitacora?id_empresa=$idEmpresa" -Token $token
}
Add-Result 'Auditoria' 'GET bitacora' $audit.Ok ($(if ($audit.Ok) { 'ok' } else { DetailOf $audit }))

# --- Crear empresa (usuario nuevo) + plan gratuito ---
$tokenN = $null
if ($loginNew.Ok) {
    $tokenN = $loginNew.Data.access_token
    if (-not $tokenN) { $tokenN = $loginNew.Data.accessToken }
}
if ($tokenN) {
    $nit = (Get-Random -Minimum 1000000 -Maximum 9999999).ToString()
    $crear = Invoke-Api -Method POST -Path '/api/empresas/crear' -Token $tokenN -Body @{
        nombre       = "Empresa E2E $stamp"
        razon_social = "Empresa E2E $stamp SRL"
        nit          = $nit
        correo       = $newEmail
    }
    $idNueva = $null
    if ($crear.Ok) {
        $idNueva = $crear.Data.id_empresa
        if (-not $idNueva) { $idNueva = $crear.Data.idEmpresa }
        if (-not $idNueva -and $crear.Data.empresa) {
            $idNueva = $crear.Data.empresa.id_empresa
            if (-not $idNueva) { $idNueva = $crear.Data.empresa.idEmpresa }
        }
    }
    Add-Result 'Onboarding' 'POST crear empresa' ($null -ne $idNueva) ($(if ($idNueva) { "id=$idNueva" } else { DetailOf $crear }))

    $freePlan = $null
    if ($planes.Ok) {
        $freePlan = @($planes.Data) | Where-Object {
            $pr = $_.precio; if ($null -eq $pr) { $pr = 0 }
            [decimal]$pr -eq 0
        } | Select-Object -First 1
    }
    if ($idNueva -and $freePlan) {
        $idPlan = $freePlan.id_plan; if (-not $idPlan) { $idPlan = $freePlan.idPlan }
        $chk = Invoke-Api -Method POST -Path '/api/pagos/checkout' -Token $tokenN -Body @{
            id_plan     = [int]$idPlan
            id_empresa  = [int]$idNueva
        }
        Add-Result 'Onboarding' 'POST checkout plan gratuito' $chk.Ok ($(if ($chk.Ok) { "session=$($chk.Data.session_id)" } else { DetailOf $chk }))
    } else {
        Add-Result 'Onboarding' 'POST checkout plan gratuito' $false 'sin empresa o plan gratuito'
    }
}

# Resumen
$pass = @($Results | Where-Object Ok).Count
$fail = @($Results | Where-Object { -not $_.Ok }).Count
Write-Host ""
Write-Host "===== RESUMEN E2E API: $pass PASS / $fail FAIL / $($Results.Count) total =====" -ForegroundColor $(if ($fail -eq 0) { 'Green' } else { 'Yellow' })
$Results | Format-Table -AutoSize
$out = Join-Path $PSScriptRoot 'e2e-smoke-results.json'
$Results | ConvertTo-Json -Depth 4 | Set-Content -Path $out -Encoding UTF8
Write-Host "Resultados: $out"
if ($fail -gt 0) { exit 2 } else { exit 0 }
