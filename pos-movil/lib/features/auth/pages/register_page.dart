import 'package:flutter/material.dart';

import '../../../app/router.dart';
import '../../../core/api_service.dart';
import '../../../core/constants/app_palette.dart';
import '../services/auth_service.dart';

class RegisterPage extends StatefulWidget {
  const RegisterPage({super.key});

  @override
  State<RegisterPage> createState() => _RegisterPageState();
}

class _RegisterPageState extends State<RegisterPage> {
  final _formKey = GlobalKey<FormState>();
  final _emailController = TextEditingController();
  final _passwordController = TextEditingController();
  final _nameController = TextEditingController();
  final _birthDateController = TextEditingController();
  final _phoneController = TextEditingController();
  final _documentController = TextEditingController();
  final _authService = AuthService();

  bool _isLoading = false;
  bool _isPasswordVisible = false;
  String? _errorMessage;
  DateTime? _selectedBirthDate;
  String? _selectedGender;

  @override
  void dispose() {
    _emailController.dispose();
    _passwordController.dispose();
    _nameController.dispose();
    _birthDateController.dispose();
    _phoneController.dispose();
    _documentController.dispose();
    super.dispose();
  }

  Future<void> _pickBirthDate() async {
    final now = DateTime.now();
    final initialDate =
        _selectedBirthDate ?? DateTime(now.year - 18, now.month, now.day);
    final firstDate = DateTime(1900);
    final lastDate = DateTime(now.year, now.month, now.day);

    final picked = await showDatePicker(
      context: context,
      initialDate: initialDate.isAfter(lastDate) ? lastDate : initialDate,
      firstDate: firstDate,
      lastDate: lastDate,
    );

    if (picked == null) {
      return;
    }

    setState(() {
      _selectedBirthDate = picked;
      _birthDateController.text = _formatDate(picked);
    });
  }

  String _formatDate(DateTime date) {
    final year = date.year.toString().padLeft(4, '0');
    final month = date.month.toString().padLeft(2, '0');
    final day = date.day.toString().padLeft(2, '0');
    return '$year-$month-$day';
  }

  Future<void> _submit() async {
    final form = _formKey.currentState;
    if (form == null || !form.validate()) {
      return;
    }

    setState(() {
      _isLoading = true;
      _errorMessage = null;
    });

    try {
      final response = await _authService.register(
        email: _emailController.text.trim(),
        contrasena: _passwordController.text,
        nombreCompleto: _nameController.text.trim(),
        fechaNacimiento: _birthDateController.text.trim(),
        genero: _selectedGender!,
        telefono: _phoneController.text.trim(),
        documento: _documentController.text.trim(),
      );

      if (!mounted) return;

      await showDialog<void>(
        context: context,
        builder: (dialogContext) {
          return AlertDialog(
            title: const Text('Registro exitoso'),
            content: Text(
              response.mensaje.isEmpty
                  ? 'Tu cuenta fue creada correctamente.'
                  : response.mensaje,
            ),
            actions: [
              TextButton(
                onPressed: () {
                  Navigator.of(dialogContext).pop();
                },
                child: const Text('Aceptar'),
              ),
            ],
          );
        },
      );

      if (!mounted) return;
      Navigator.of(context).pushReplacementNamed(AppRouter.login);
    } on ApiException catch (error) {
      _showError(error.message);
    } catch (_) {
      _showError('No se pudo registrar al usuario');
    } finally {
      if (mounted) {
        setState(() => _isLoading = false);
      }
    }
  }

  void _showError(String message) {
    if (!mounted) return;

    setState(() => _errorMessage = message);
  }

  Future<void> _openVerifyCodeModal() async {
    await showDialog<void>(
      context: context,
      builder: (_) => _VerifyCodeDialog(
        authService: _authService,
        initialEmail: _emailController.text.trim(),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppPalette.bg,
      appBar: AppBar(title: const Text('Registro')),
      body: SafeArea(
        child: Center(
          child: SingleChildScrollView(
            padding: const EdgeInsets.all(24),
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 480),
              child: DecoratedBox(
                decoration: BoxDecoration(
                  color: AppPalette.surface,
                  borderRadius: BorderRadius.circular(8),
                  border: Border.all(color: AppPalette.border),
                  boxShadow: [
                    BoxShadow(
                      color: AppPalette.text.withValues(alpha: 0.08),
                      blurRadius: 24,
                      offset: const Offset(0, 12),
                    ),
                  ],
                ),
                child: Padding(
                  padding: const EdgeInsets.all(24),
                  child: Form(
                    key: _formKey,
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.stretch,
                      children: [
                        const Icon(
                          Icons.person_add_alt_1,
                          color: AppPalette.primary,
                          size: 48,
                        ),
                        const SizedBox(height: 16),
                        Text(
                          'Crear cuenta',
                          textAlign: TextAlign.center,
                          style: Theme.of(context).textTheme.headlineSmall
                              ?.copyWith(
                                color: AppPalette.text,
                                fontWeight: FontWeight.w800,
                              ),
                        ),
                        const SizedBox(height: 8),
                        Text(
                          'Completa tus datos para registrarte',
                          textAlign: TextAlign.center,
                          style: Theme.of(context).textTheme.bodyMedium
                              ?.copyWith(color: AppPalette.textSoft),
                        ),
                        const SizedBox(height: 28),
                        TextFormField(
                          controller: _nameController,
                          textInputAction: TextInputAction.next,
                          decoration: const InputDecoration(
                            labelText: 'Nombre completo',
                            prefixIcon: Icon(Icons.badge_outlined),
                          ),
                          validator: (value) {
                            if (value == null || value.trim().isEmpty) {
                              return 'Ingresa tu nombre completo';
                            }
                            return null;
                          },
                        ),
                        const SizedBox(height: 16),
                        TextFormField(
                          controller: _emailController,
                          keyboardType: TextInputType.emailAddress,
                          textInputAction: TextInputAction.next,
                          decoration: const InputDecoration(
                            labelText: 'Email',
                            prefixIcon: Icon(Icons.email_outlined),
                          ),
                          validator: (value) {
                            final email = value?.trim() ?? '';
                            if (email.isEmpty) {
                              return 'Ingresa tu email';
                            }
                            if (!email.contains('@')) {
                              return 'Ingresa un email valido';
                            }
                            return null;
                          },
                        ),
                        const SizedBox(height: 16),
                        TextFormField(
                          controller: _passwordController,
                          obscureText: !_isPasswordVisible,
                          textInputAction: TextInputAction.next,
                          decoration: InputDecoration(
                            labelText: 'Contrasena',
                            prefixIcon: const Icon(Icons.lock_outline),
                            suffixIcon: IconButton(
                              tooltip: _isPasswordVisible
                                  ? 'Ocultar contrasena'
                                  : 'Mostrar contrasena',
                              onPressed: () {
                                setState(() {
                                  _isPasswordVisible = !_isPasswordVisible;
                                });
                              },
                              icon: Icon(
                                _isPasswordVisible
                                    ? Icons.visibility_off_outlined
                                    : Icons.visibility_outlined,
                              ),
                            ),
                          ),
                          validator: (value) {
                            if (value == null || value.isEmpty) {
                              return 'Ingresa tu contrasena';
                            }
                            if (value.length < 6) {
                              return 'Usa al menos 6 caracteres';
                            }
                            return null;
                          },
                        ),
                        const SizedBox(height: 16),
                        TextFormField(
                          controller: _birthDateController,
                          readOnly: true,
                          onTap: _pickBirthDate,
                          decoration: const InputDecoration(
                            labelText: 'Fecha de nacimiento',
                            prefixIcon: Icon(Icons.cake_outlined),
                            suffixIcon: Icon(Icons.calendar_month_outlined),
                          ),
                          validator: (value) {
                            if (value == null || value.trim().isEmpty) {
                              return 'Selecciona tu fecha de nacimiento';
                            }
                            return null;
                          },
                        ),
                        const SizedBox(height: 16),
                        DropdownButtonFormField<String>(
                          value: _selectedGender,
                          items: const [
                            DropdownMenuItem(
                              value: 'M',
                              child: Text('Masculino'),
                            ),
                            DropdownMenuItem(
                              value: 'F',
                              child: Text('Femenino'),
                            ),
                            DropdownMenuItem(value: 'O', child: Text('Otro')),
                          ],
                          onChanged: _isLoading
                              ? null
                              : (value) {
                                  setState(() {
                                    _selectedGender = value;
                                  });
                                },
                          decoration: const InputDecoration(
                            labelText: 'Genero',
                            prefixIcon: Icon(Icons.wc_outlined),
                          ),
                          validator: (value) {
                            if (value == null || value.isEmpty) {
                              return 'Selecciona tu genero';
                            }
                            return null;
                          },
                        ),
                        const SizedBox(height: 16),
                        TextFormField(
                          controller: _phoneController,
                          keyboardType: TextInputType.phone,
                          textInputAction: TextInputAction.next,
                          decoration: const InputDecoration(
                            labelText: 'Telefono',
                            prefixIcon: Icon(Icons.phone_outlined),
                          ),
                          validator: (value) {
                            if (value == null || value.trim().isEmpty) {
                              return 'Ingresa tu telefono';
                            }
                            return null;
                          },
                        ),
                        const SizedBox(height: 16),
                        TextFormField(
                          controller: _documentController,
                          keyboardType: TextInputType.number,
                          textInputAction: TextInputAction.done,
                          decoration: const InputDecoration(
                            labelText: 'Documento',
                            prefixIcon: Icon(Icons.credit_card_outlined),
                          ),
                          validator: (value) {
                            if (value == null || value.trim().isEmpty) {
                              return 'Ingresa tu documento';
                            }
                            return null;
                          },
                        ),
                        if (_errorMessage != null) ...[
                          const SizedBox(height: 16),
                          _ErrorMessage(message: _errorMessage!),
                        ],
                        const SizedBox(height: 24),
                        ElevatedButton(
                          onPressed: _isLoading ? null : _submit,
                          child: _isLoading
                              ? const SizedBox(
                                  width: 22,
                                  height: 22,
                                  child: CircularProgressIndicator(
                                    strokeWidth: 2.5,
                                    color: AppPalette.text,
                                  ),
                                )
                              : const Text('Registrarme'),
                        ),
                        const SizedBox(height: 12),
                        OutlinedButton.icon(
                          onPressed: _isLoading ? null : _openVerifyCodeModal,
                          icon: const Icon(Icons.verified_user_outlined),
                          label: const Text('Ingresar codigo de verificacion'),
                          style: OutlinedButton.styleFrom(
                            foregroundColor: AppPalette.text,
                            side: const BorderSide(color: AppPalette.primary),
                            minimumSize: const Size.fromHeight(48),
                            shape: RoundedRectangleBorder(
                              borderRadius: BorderRadius.circular(8),
                            ),
                          ),
                        ),
                        const SizedBox(height: 16),
                        TextButton(
                          onPressed: _isLoading
                              ? null
                              : () {
                                  Navigator.of(context).pop();
                                },
                          child: const Text('Volver al inicio de sesion'),
                        ),
                      ],
                    ),
                  ),
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class _VerifyCodeDialog extends StatefulWidget {
  final AuthService authService;
  final String initialEmail;

  const _VerifyCodeDialog({
    required this.authService,
    required this.initialEmail,
  });

  @override
  State<_VerifyCodeDialog> createState() => _VerifyCodeDialogState();
}

class _VerifyCodeDialogState extends State<_VerifyCodeDialog> {
  final _formKey = GlobalKey<FormState>();
  late final TextEditingController _emailController;
  final _codeController = TextEditingController();

  bool _isLoading = false;
  String? _errorMessage;
  String? _successMessage;

  @override
  void initState() {
    super.initState();
    _emailController = TextEditingController(text: widget.initialEmail);
  }

  @override
  void dispose() {
    _emailController.dispose();
    _codeController.dispose();
    super.dispose();
  }

  Future<void> _verifyCode() async {
    final form = _formKey.currentState;
    if (form == null || !form.validate()) {
      return;
    }

    setState(() {
      _isLoading = true;
      _errorMessage = null;
      _successMessage = null;
    });

    try {
      final response = await widget.authService.verifyCode(
        email: _emailController.text.trim(),
        codigo: _codeController.text.trim(),
      );

      if (!mounted) return;

      setState(() {
        _successMessage = response.mensaje.isEmpty
            ? 'Cuenta verificada correctamente.'
            : response.mensaje;
      });
    } on ApiException catch (error) {
      _showError(error.message);
    } catch (_) {
      _showError('Error al verificar el codigo.');
    } finally {
      if (mounted) {
        setState(() => _isLoading = false);
      }
    }
  }

  void _showError(String message) {
    if (!mounted) return;
    setState(() => _errorMessage = message);
  }

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      backgroundColor: AppPalette.surface,
      title: const Text('Verificar cuenta'),
      content: Form(
        key: _formKey,
        child: SingleChildScrollView(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              TextFormField(
                controller: _emailController,
                keyboardType: TextInputType.emailAddress,
                textInputAction: TextInputAction.next,
                decoration: const InputDecoration(
                  labelText: 'Email',
                  prefixIcon: Icon(Icons.email_outlined),
                ),
                validator: (value) {
                  final email = value?.trim() ?? '';
                  if (email.isEmpty) {
                    return 'Ingresa tu email';
                  }
                  if (!email.contains('@')) {
                    return 'Ingresa un email valido';
                  }
                  return null;
                },
              ),
              const SizedBox(height: 16),
              TextFormField(
                controller: _codeController,
                textInputAction: TextInputAction.done,
                onFieldSubmitted: (_) => _verifyCode(),
                decoration: const InputDecoration(
                  labelText: 'Codigo',
                  prefixIcon: Icon(Icons.pin_outlined),
                ),
                validator: (value) {
                  if (value == null || value.trim().isEmpty) {
                    return 'Ingresa el codigo de verificacion';
                  }
                  return null;
                },
              ),
              if (_errorMessage != null) ...[
                const SizedBox(height: 16),
                _ErrorMessage(message: _errorMessage!),
              ],
              if (_successMessage != null) ...[
                const SizedBox(height: 16),
                _SuccessMessage(message: _successMessage!),
              ],
            ],
          ),
        ),
      ),
      actions: [
        TextButton(
          onPressed: _isLoading ? null : () => Navigator.of(context).pop(),
          child: const Text('Cerrar'),
        ),
        ElevatedButton(
          onPressed: _isLoading ? null : _verifyCode,
          style: ElevatedButton.styleFrom(
            minimumSize: const Size(112, 44),
          ),
          child: _isLoading
              ? const SizedBox(
                  width: 20,
                  height: 20,
                  child: CircularProgressIndicator(
                    strokeWidth: 2.5,
                    color: AppPalette.text,
                  ),
                )
              : const Text('Verificar'),
        ),
      ],
    );
  }
}

class _ErrorMessage extends StatelessWidget {
  final String message;

  const _ErrorMessage({required this.message});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
      decoration: BoxDecoration(
        color: AppPalette.danger.withValues(alpha: 0.08),
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: AppPalette.danger.withValues(alpha: 0.35)),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Icon(
            Icons.error_outline,
            color: AppPalette.danger,
            size: 20,
          ),
          const SizedBox(width: 10),
          Expanded(
            child: Text(
              message,
              style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                color: AppPalette.danger,
                fontWeight: FontWeight.w600,
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _SuccessMessage extends StatelessWidget {
  final String message;

  const _SuccessMessage({required this.message});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
      decoration: BoxDecoration(
        color: AppPalette.success.withValues(alpha: 0.08),
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: AppPalette.success.withValues(alpha: 0.35)),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Icon(
            Icons.check_circle_outline,
            color: AppPalette.success,
            size: 20,
          ),
          const SizedBox(width: 10),
          Expanded(
            child: Text(
              message,
              style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                    color: AppPalette.success,
                    fontWeight: FontWeight.w600,
                  ),
            ),
          ),
        ],
      ),
    );
  }
}
