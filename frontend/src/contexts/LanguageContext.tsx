import React, { createContext, useContext, useEffect, useState, ReactNode, useMemo } from "react";
import { storage } from "@/src/utils/storage";
import { TRANSLATIONS, LangCode } from "@/src/i18n/translations";

type LanguageCtx = {
  lang: LangCode;
  setLang: (l: LangCode) => Promise<void>;
  t: (key: string) => string;
  ready: boolean;
};

const Ctx = createContext<LanguageCtx | null>(null);

export function LanguageProvider({ children }: { children: ReactNode }) {
  const [lang, setLangState] = useState<LangCode>("es");
  const [ready, setReady] = useState(false);

  useEffect(() => {
    (async () => {
      const saved = await storage.getItem<string>("@app/lang", "es");
      if (saved && (saved in TRANSLATIONS)) {
        setLangState(saved as LangCode);
      }
      setReady(true);
    })();
  }, []);

  const setLang = async (l: LangCode) => {
    setLangState(l);
    await storage.setItem("@app/lang", l);
  };

  const value = useMemo<LanguageCtx>(() => ({
    lang,
    setLang,
    ready,
    t: (key: string) => TRANSLATIONS[lang][key] ?? TRANSLATIONS.en[key] ?? key,
  }), [lang, ready]);

  return <Ctx.Provider value={value}>{children}</Ctx.Provider>;
}

export function useLanguage() {
  const v = useContext(Ctx);
  if (!v) throw new Error("useLanguage must be used inside LanguageProvider");
  return v;
}
