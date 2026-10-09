import { createContext, useContext, useEffect, useState, type ReactNode } from 'react';
import { api, ApiError } from './api';
import type { User } from './types';

type Session =
  | { status: 'checking' }
  | { status: 'guest'; reason?: string }
  | { status: 'error'; message: string }
  | { status: 'ready'; user: User };

interface SessionContextValue {
  session: Session;
  retryRestore: () => void;
  signIn: (email: string, password: string) => Promise<void>;
  signOut: () => Promise<void>;
  reconcileUser: () => Promise<void>;
}

const SessionContext = createContext<SessionContextValue | null>(null);

export function SessionProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<Session>({ status: 'checking' });
  const [restoreKey, setRestoreKey] = useState(0);

  useEffect(() => {
    let active = true;
    api.onSessionExpired = () => {
      if (active) setSession({ status: 'guest', reason: 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.' });
    };
    api.restore().then(
      async user => {
        if (import.meta.env.VITE_SOLANA_CLUSTER === 'localnet') await api.autoSolanaWallet().catch(() => undefined);
        if (active) setSession({ status: 'ready', user });
      },
      error => {
        if (!active) return;
        api.clear();
        if (error instanceof ApiError && error.status === 401) {
          setSession({ status: 'guest' });
        } else {
          setSession({ status: 'error', message: error instanceof Error ? error.message : 'Không thể kiểm tra phiên đăng nhập.' });
        }
      },
    );
    return () => { active = false; api.onSessionExpired = null; };
  }, [restoreKey]);

  const value: SessionContextValue = {
    session,
    retryRestore: () => {
      setSession({ status: 'checking' });
      setRestoreKey(key => key + 1);
    },
    signIn: async (email, password) => {
      const user = await api.signIn(email, password);
      if (import.meta.env.VITE_SOLANA_CLUSTER === 'localnet') await api.autoSolanaWallet().catch(() => undefined);
      setSession({ status: 'ready', user });
    },
    signOut: async () => {
      await api.signOut();
      setSession({ status: 'guest' });
    },
    reconcileUser: async () => {
      const trusted = await api.me();
      if (session.status !== 'ready' || trusted.id !== session.user.id) throw new Error('Không thể đối chiếu danh tính phiên.');
      setSession(current => current.status === 'ready' && current.user.id === trusted.id ? { status: 'ready', user: trusted } : current);
    },
  };

  return <SessionContext.Provider value={value}>{children}</SessionContext.Provider>;
}

export function useSession(): SessionContextValue {
  const value = useContext(SessionContext);
  if (!value) throw new Error('SessionProvider is missing.');
  return value;
}
