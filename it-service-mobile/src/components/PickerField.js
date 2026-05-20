import { useState } from 'react';
import { View, Text, Pressable, Modal, FlatList, StyleSheet } from 'react-native';
import { useTheme } from '../theme/ThemeContext';

/**
 * Etiketli seçim alanı — dokununca modal liste açar. options: [{ label, value }].
 * React Native'de native <select> olmadığı için modal-liste deseni kullanılır.
 */
export default function PickerField({
  label,
  placeholder,
  value,
  options,
  onChange,
  disabled,
}) {
  const { theme } = useTheme();
  const [open, setOpen] = useState(false);
  const selected = options.find((o) => o.value === value);

  return (
    <View style={styles.wrap}>
      <Text style={[styles.label, { color: theme.textPrimary }]}>{label}</Text>
      <Pressable
        disabled={disabled}
        onPress={() => setOpen(true)}
        style={[
          styles.field,
          { backgroundColor: theme.bgInput, borderColor: theme.border, opacity: disabled ? 0.5 : 1 },
        ]}
      >
        <Text
          style={{ flex: 1, fontSize: 14, color: selected ? theme.textPrimary : theme.textTertiary }}
          numberOfLines={1}
        >
          {selected ? selected.label : placeholder}
        </Text>
        <Text style={{ color: theme.textTertiary, fontSize: 12 }}>▼</Text>
      </Pressable>

      <Modal visible={open} transparent animationType="fade" onRequestClose={() => setOpen(false)}>
        <Pressable
          style={[styles.backdrop, { backgroundColor: theme.overlay }]}
          onPress={() => setOpen(false)}
        >
          <View style={[styles.sheet, { backgroundColor: theme.bgSurface }]}>
            <Text style={[styles.sheetTitle, { color: theme.textPrimary }]}>{label}</Text>
            <FlatList
              data={options}
              keyExtractor={(o) => String(o.value)}
              style={{ maxHeight: 320 }}
              renderItem={({ item }) => {
                const active = item.value === value;
                return (
                  <Pressable
                    onPress={() => {
                      onChange(item.value);
                      setOpen(false);
                    }}
                    style={[styles.option, { borderBottomColor: theme.border }]}
                  >
                    <Text
                      style={{
                        fontSize: 15,
                        color: active ? theme.primary : theme.textPrimary,
                        fontWeight: active ? '700' : '400',
                      }}
                    >
                      {item.label}
                    </Text>
                  </Pressable>
                );
              }}
              ListEmptyComponent={
                <Text style={{ color: theme.textTertiary, padding: 16, textAlign: 'center' }}>—</Text>
              }
            />
          </View>
        </Pressable>
      </Modal>
    </View>
  );
}

const styles = StyleSheet.create({
  wrap: { gap: 6 },
  label: { fontSize: 14, fontWeight: '600' },
  field: {
    flexDirection: 'row',
    alignItems: 'center',
    borderWidth: 1,
    borderRadius: 10,
    paddingHorizontal: 12,
    height: 46,
  },
  backdrop: { flex: 1, justifyContent: 'center', padding: 28 },
  sheet: { borderRadius: 14, padding: 16, gap: 8 },
  sheetTitle: { fontSize: 16, fontWeight: '700' },
  option: { paddingVertical: 13, borderBottomWidth: StyleSheet.hairlineWidth },
});
