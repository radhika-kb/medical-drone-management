import React, { createContext, useContext, useState, useEffect } from 'react';
import { authService } from '../services/api';

const AuthContext = createContext(null);

export const AuthProvider = ({ children }) => {
  const [user, setUser] = useState(null);
  const [token, setToken] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const savedToken = localStorage.getItem('token');
    const savedUser = localStorage.getItem('user');

    if (savedToken && savedUser) {
      try {
        setToken(savedToken);
        setUser(JSON.parse(savedUser));
      } catch (e) {
        localStorage.removeItem('token');
        localStorage.removeItem('user');
      }
    }
    setLoading(false);
  }, []);

  const login = async (email, password) => {
    try {
      const data = await authService.login(email, password);
      const authToken = data.accessToken || data.token;
      const authUser = data.user || {
        email: data.email || email,
        fullName: data.name || data.fullName || 'Medical Operator',
        id: data.userId,
        role: data.role,
        hospitalId: data.hospitalId,
        hospitalName: data.hospitalName,
      };

      setToken(authToken);
      setUser(authUser);
      localStorage.setItem('token', authToken);
      localStorage.setItem('user', JSON.stringify(authUser));
      return { success: true, user: authUser };
    } catch (err) {
      console.error('Login error:', err);
      return {
        success: false,
        error: err.response?.data?.message || 'Invalid credentials or server unavailable',
      };
    }
  };

  const register = async (account) => {
    try {
      const data = await authService.register(account);
      const authToken = data.accessToken || data.token;
      const authUser = {
        id: data.userId,
        email: data.email,
        fullName: data.name,
        role: data.role,
        hospitalId: data.hospitalId,
        hospitalName: data.hospitalName,
      };
      setToken(authToken);
      setUser(authUser);
      localStorage.setItem('token', authToken);
      localStorage.setItem('user', JSON.stringify(authUser));
      return { success: true, user: authUser };
    } catch (err) {
      return { success: false, error: err.response?.data?.message || 'Could not create the account.' };
    }
  };

  const logout = () => {
    setToken(null);
    setUser(null);
    localStorage.removeItem('token');
    localStorage.removeItem('user');
  };

  const hasRole = (requiredRole) => {
    if (!user) return false;
    if (user.role === 'ROLE_ADMIN') return true; // Superuser bypass
    return user.role === requiredRole;
  };

  return (
    <AuthContext.Provider
      value={{
        user,
        token,
        loading,
        isAuthenticated: !!user,
        login,
        register,
        logout,
        hasRole,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = () => {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
};
