import axios from 'axios';
import { useState } from 'react';
import { View, Text, TextInput, TouchableOpacity, StyleSheet, ActivityIndicator, Image, KeyboardAvoidingView, Platform } from 'react-native';
import { Redirect } from 'expo-router';

import { useAuth } from '@/context/AuthContext';
import { login as authenticate } from '@/services/api';

export default function LoginScreen() {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const [errorMsg, setErrorMsg] = useState('');
  const { login, userToken } = useAuth();

  if (userToken) {
    return <Redirect href="/(tabs)" />;
  }

  const handleLogin = async (): Promise<void> => {
    setErrorMsg('');
    if (!email || !password) {
      setErrorMsg('Please enter both email and password.');
      return;
    }
    setLoading(true);
    try {
      const response = await authenticate(email, password);
      await login(
        response.data.token,
        response.data.role,
        response.data.firstName ?? '',
        response.data.lastName ?? '',
        response.data.schoolName ?? null,
        response.data.academicYearName ?? null,
      );
    } catch (error) {
      if (axios.isAxiosError(error) && error.response?.status === 401) {
        setErrorMsg('Incorrect email or password.');
      } else {
        setErrorMsg('Network error. Please try again.');
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <KeyboardAvoidingView
      style={styles.root}
      behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
    >
      <View style={styles.container}>

        {/* Logo block */}
        <View style={styles.logoBlock}>
          <View style={styles.logoClip}>
            <Image
              source={require('../assets/images/acadia-logo.png')}
              style={styles.logo}
              resizeMode="contain"
            />
          </View>
          <Text style={styles.brandWord}>ACADIA</Text>
          <Text style={styles.tagline}>Connect · Manage · Empower</Text>
          <Text style={styles.subtitle}>School Management Platform</Text>
          <View style={styles.divider} />
        </View>

        {/* Form block */}
        <View style={styles.formBlock}>
          <Text style={styles.signInLabel}>Sign in to your account</Text>

          <View style={styles.inputWrap}>
            <Text style={styles.inputLabel}>Email</Text>
            <TextInput
              style={styles.input}
              placeholder="Email / Username"
              placeholderTextColor="#475569"
              value={email}
              onChangeText={setEmail}
              autoCapitalize="none"
              keyboardType="email-address"
            />
          </View>

          <View style={styles.inputWrap}>
            <Text style={styles.inputLabel}>Password</Text>
            <TextInput
              style={styles.input}
              placeholder="Password"
              placeholderTextColor="#475569"
              secureTextEntry
              value={password}
              onChangeText={setPassword}
            />
          </View>

          {errorMsg ? (
            <View style={styles.errorWrap}>
              <Text style={styles.errorText}>{errorMsg}</Text>
            </View>
          ) : null}

          <TouchableOpacity
            style={[styles.button, loading && styles.buttonDisabled]}
            onPress={handleLogin}
            disabled={loading}
            activeOpacity={0.85}
          >
            {loading ? (
              <ActivityIndicator color="#fff" />
            ) : (
              <Text style={styles.buttonText}>Log In</Text>
            )}
          </TouchableOpacity>
        </View>

        {/* Footer */}
        <Text style={styles.footer}>Secure Portal · School Management System</Text>

      </View>
    </KeyboardAvoidingView>
  );
}

const styles = StyleSheet.create({
  root: {
    flex: 1,
    backgroundColor: '#F7F9FC',
  },
  container: {
    flex: 1,
    paddingHorizontal: 28,
    justifyContent: 'center',
  },
  logoBlock: {
    alignItems: 'center',
    marginBottom: 40,
  },
  logoClip: {
    width: 140,
    height: 90,
    overflow: 'hidden',
    alignItems: 'center',
  },
  logo: {
    width: 140,
    height: 140,
  },
  brandWord: {
    fontSize: 18,
    fontWeight: '700',
    letterSpacing: 5,
    color: '#0F172A',
    marginTop: 4,
  },
  tagline: {
    fontSize: 10,
    fontWeight: '600',
    letterSpacing: 2,
    color: '#64748B',
    textTransform: 'uppercase',
    marginTop: 4,
  },
  subtitle: {
    fontSize: 12,
    color: '#94A3B8',
    letterSpacing: 1.5,
    textTransform: 'uppercase',
    marginTop: 14,
    marginBottom: 20,
  },
  divider: {
    width: 40,
    height: 1,
    backgroundColor: '#E2E8F0',
  },
  formBlock: {
    backgroundColor: '#FFFFFF',
    borderRadius: 20,
    padding: 24,
    borderWidth: 1,
    borderColor: '#E2E8F0',
    marginBottom: 24,
  },
  signInLabel: {
    fontSize: 15,
    fontWeight: '600',
    color: '#64748B',
    marginBottom: 20,
  },
  inputWrap: {
    marginBottom: 16,
  },
  inputLabel: {
    fontSize: 12,
    fontWeight: '600',
    color: '#64748b',
    letterSpacing: 0.5,
    marginBottom: 6,
    textTransform: 'uppercase',
  },
  input: {
    backgroundColor: '#F7F9FC',
    borderRadius: 10,
    padding: 14,
    color: '#0F172A',
    fontSize: 15,
    borderWidth: 1,
    borderColor: '#E2E8F0',
  },
  errorWrap: {
    backgroundColor: '#ef444415',
    borderRadius: 8,
    padding: 10,
    marginBottom: 12,
    borderWidth: 1,
    borderColor: '#ef444430',
  },
  errorText: {
    color: '#ef4444',
    fontSize: 13,
    textAlign: 'center',
  },
  button: {
    backgroundColor: '#6366f1',
    padding: 16,
    borderRadius: 12,
    alignItems: 'center',
    marginTop: 4,
  },
  buttonDisabled: {
    opacity: 0.6,
  },
  buttonText: {
    color: '#FFFFFF',
    fontSize: 16,
    fontWeight: '700',
    letterSpacing: 0.5,
  },
  footer: {
    textAlign: 'center',
    fontSize: 12,
    color: '#E2E8F0',
    letterSpacing: 0.5,
  },
});