# Guion para demostrar pagos con cheque en el POS

## Antes de mostrarlo

Usen la instancia con los cambios de cheques y una caja abierta. Tengan a mano un producto con stock y un cliente de demostración con una cuenta por cobrar si van a mostrar abonos. Los datos de cheque de esta guía son ficticios.

**Para empezar, digan**

> En el punto de venta podemos registrar un cheque como pago total, combinarlo con otro medio de pago o usarlo para abonar una deuda. El cajero registra los datos del cheque y confirma manualmente que ya fue cobrado antes de aplicar el pago.

## 1. Mostrar una venta pagada íntegramente con cheque

1. En **Punto de venta**, seleccionen **Contado** y agreguen un producto. En el ejemplo, un **Refresco 2 L** cuesta **Bs 12,00**.
2. Si van a mostrar la factura simulada, seleccionen un cliente y activen **Factura en Línea**.
3. Pulsen **Cobrar**, debajo de **Total a Cobrar**. En **Registrar pago**, elijan **CHEQUE** en el selector **Método de pago**.
4. Completen los campos mientras explican cada dato.

| Campo | Qué ingresar en la demo | Qué decirle al docente |
| --- | --- | --- |
| **Monto** | **Bs 12,00** | «Este es el importe que se paga con el cheque. Como cubre toda la venta, coincide con el total a cobrar». |
| **Banco** | **Banco de demostración** | «Aquí registramos el banco que figura en el cheque para identificar la entidad vinculada al pago». |
| **Número de cheque** | **000123** | «Copiamos el número tal como está impreso. Conservamos los ceros iniciales para que la referencia coincida con el documento». |
| **Fecha de emisión** | **20/11/2026** | «Esta es la fecha en que se emitió el cheque. El sistema registra por separado la fecha y hora en que aplicamos el pago». |
| **Confirmo que el cheque ya fue cobrado** | Marquen la casilla | «En este ejemplo damos por verificado el cobro. Al marcar la casilla, el cajero deja constancia de esa confirmación para poder registrar la venta como pagada». |

5. Pulsen **Registrar**. En el **Historial de ventas**, abran **Ver detalle** para mostrar el medio de pago, el importe y los datos del cheque. Si emitieron una factura simulada, muestren que el PDF dice **CHEQUE**.

> Aquí vemos la venta registrada y podemos identificar el cheque con el que se pagó. En el cierre de caja, su importe figura en la fila Cheque, separado del efectivo.

## 2. Mostrar un pago mixto

1. Preparen otra venta de **Bs 12,00**. En **Cobrar**, elijan **EFECTIVO** y asignen **Bs 5,00** a la primera fila.
2. Pulsen **+ (Agregar forma de pago)**, elijan **CHEQUE** y asignen **Bs 7,00** a la segunda fila. Usen el cheque ficticio **000124** y confirmen su cobro.
3. Pulsen **Registrar** y muestren ambos importes en el detalle de la venta.

> La venta suma Bs 12,00. Registramos Bs 5,00 en efectivo y Bs 7,00 por cheque. El POS guarda cada importe con su medio y valida que ambos sumen el total de la venta.

## 3. Mostrar un abono a crédito con cheque

1. En la caja abierta, entren a **Cobro de Créditos**. Elijan un cliente con una cuenta pendiente, por ejemplo, de **Bs 12,00**. Pulsen **Cobrar** en esa cuenta.
2. Seleccionen **CHEQUE**, indiquen un abono de **Bs 5,00** y completen los datos con el número ficticio **000125**. Marquen la confirmación y pulsen **Registrar**.
3. Muestren que el **saldo pendiente** queda en **Bs 7,00**.

> Estamos cobrando una parte de una venta anterior. El cheque aplica Bs 5,00 a la cuenta por cobrar y el cliente queda debiendo Bs 7,00.

## Si el docente pregunta

- **¿Qué pasa si la casilla está vacía?** «El POS pide confirmar el cobro del cheque para continuar. Puedo mostrarlo pulsando Registrar antes de marcarla».
- **¿Quién verifica el cobro?** «El cajero confirma el cobro con la información que tiene del banco. La casilla registra una confirmación manual».
- **¿Cómo se refleja en el SIN?** «En nuestra factura simulada, una venta pagada enteramente con cheque usa el código `3`. Para efectivo más cheque usamos el código `11`. El detalle de los importes queda en el POS».
