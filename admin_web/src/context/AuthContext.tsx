import React, { createContext, useContext, useEffect, useState } from 'react';
import type { User, Session } from '@supabase/supabase-js';
import { supabase } from '../lib/supabase';
import type { StaffUser } from '../types';

interface AuthContextType {
  user: User | null;
  session: Session | null;
  staffUser: StaffUser | null;
  loading: boolean;
  signIn: (email: string, password: string) => Promise<{ error: Error | null }>;
  signOut: () => Promise<void>;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [user, setUser] = useState<User | null>(null);
  const [session, setSession] = useState<Session | null>(null);
  const [staffUser, setStaffUser] = useState<StaffUser | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let profileRequest = 0;
    let profileTimer: ReturnType<typeof setTimeout>;

    const loadStaffProfile = async (uid: string, request: number) => {
      try {
        const { data, error } = await supabase
          .from('staff_user')
          .select('*')
          .eq('uid', uid)
          .maybeSingle();

        if (request !== profileRequest) return;
        setStaffUser(!error && data?.uid === uid ? data as StaffUser : null);
      } catch {
        if (request === profileRequest) setStaffUser(null);
      } finally {
        if (request === profileRequest) setLoading(false);
      }
    };

    const { data: { subscription } } = supabase.auth.onAuthStateChange((_event, session) => {
      const request = ++profileRequest;
      clearTimeout(profileTimer);
      setSession(session);
      setUser(session?.user ?? null);
      setStaffUser(null);
      setLoading(Boolean(session?.user));
      if (session?.user) {
        // Run queries after the auth callback releases Supabase's session lock.
        profileTimer = setTimeout(() => loadStaffProfile(session.user.id, request), 0);
      }
    });

    return () => {
      profileRequest++;
      clearTimeout(profileTimer);
      subscription.unsubscribe();
    };
  }, []);

  const signIn = async (email: string, password: string) => {
    const { error } = await supabase.auth.signInWithPassword({ email, password });
    return { error };
  };

  const signOut = async () => {
    await supabase.auth.signOut();
    setUser(null);
    setSession(null);
    setStaffUser(null);
    localStorage.removeItem('ngopi_admin_bypass');
  };

  return (
    <AuthContext.Provider
      value={{
        user,
        session,
        staffUser,
        loading,
        signIn,
        signOut,
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
