import 'package:flutter/material.dart';
import '../../core/constants/app_palette.dart';

class ClientDrawer extends StatelessWidget {
  final String userName;
  final String userEmail;
  final int currentSectionIndex;
  final Function(int) onSectionSelected;
  final VoidCallback onLogout;
  final int unreadNotificationsCount;

  const ClientDrawer({
    super.key,
    required this.userName,
    required this.userEmail,
    required this.currentSectionIndex,
    required this.onSectionSelected,
    required this.onLogout,
    this.unreadNotificationsCount = 0,
  });

  @override
  Widget build(BuildContext context) {
    return Drawer(
      backgroundColor: AppPalette.surface,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          // Header with Avatar and User Info
          UserAccountsDrawerHeader(
            decoration: const BoxDecoration(
              color: AppPalette.bg,
            ),
            currentAccountPicture: CircleAvatar(
              backgroundColor: AppPalette.bgSoft,
              child: const Icon(
                Icons.person,
                color: AppPalette.text,
                size: 38,
              ),
            ),
            accountName: Text(
              userName,
              style: const TextStyle(
                color: AppPalette.text,
                fontWeight: FontWeight.w800,
                fontSize: 16,
              ),
            ),
            accountEmail: Text(
              userEmail,
              style: const TextStyle(
                color: AppPalette.textSoft,
                fontSize: 13,
                fontWeight: FontWeight.w500,
              ),
            ),
          ),

          // Menu Options
          Expanded(
            child: ListView(
              padding: EdgeInsets.zero,
              children: [
                _buildDrawerItem(
                  icon: Icons.home_outlined,
                  selectedIcon: Icons.home,
                  title: 'Inicio (Perfil)',
                  index: 0,
                ),
                _buildDrawerItem(
                  icon: Icons.credit_card_outlined,
                  selectedIcon: Icons.credit_card,
                  title: 'Créditos',
                  index: 1,
                ),
              ],
            ),
          ),

          const Divider(height: 1, color: AppPalette.border),

          // Logout Button
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 20),
            child: InkWell(
              borderRadius: BorderRadius.circular(10),
              onTap: () {
                Navigator.of(context).pop(); // Close drawer
                onLogout();
              },
              child: Padding(
                padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 12),
                child: Row(
                  children: [
                    Icon(
                      Icons.logout,
                      color: AppPalette.danger.withValues(alpha: 0.9),
                    ),
                    const SizedBox(width: 16),
                    Text(
                      'Cerrar sesión',
                      style: TextStyle(
                        color: AppPalette.danger.withValues(alpha: 0.9),
                        fontWeight: FontWeight.bold,
                        fontSize: 15,
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildDrawerItem({
    required IconData icon,
    required IconData selectedIcon,
    required String title,
    required int index,
    int badgeCount = 0,
  }) {
    final isSelected = currentSectionIndex == index;

    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 4),
      child: ListTile(
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(10),
        ),
        selected: isSelected,
        selectedTileColor: AppPalette.bgSoft.withValues(alpha: 0.4),
        leading: Icon(
          isSelected ? selectedIcon : icon,
          color: isSelected ? AppPalette.primary : AppPalette.textSoft,
        ),
        title: Text(
          title,
          style: TextStyle(
            color: isSelected ? AppPalette.text : AppPalette.textSoft,
            fontWeight: isSelected ? FontWeight.bold : FontWeight.w600,
            fontSize: 15,
          ),
        ),
        trailing: badgeCount > 0
            ? Container(
                padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                decoration: BoxDecoration(
                  color: AppPalette.danger,
                  borderRadius: BorderRadius.circular(10),
                ),
                child: Text(
                  badgeCount.toString(),
                  style: const TextStyle(
                    color: Colors.white,
                    fontSize: 10,
                    fontWeight: FontWeight.bold,
                  ),
                ),
              )
            : null,
        onTap: () {
          onSectionSelected(index);
        },
      ),
    );
  }
}
