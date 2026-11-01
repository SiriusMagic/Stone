// @ts-nocheck
import { ScrollViewStyleReset } from "expo-router/html";
import type { PropsWithChildren } from "react";

export default function Root({ children }: PropsWithChildren) {
  return (
    <html lang="en" style={{ height: "100%" }}>
      <head>
        <meta charSet="utf-8" />
        <meta httpEquiv="X-UA-Compatible" content="IE=edge" />
        <meta
          name="viewport"
          content="width=device-width, initial-scale=1, shrink-to-fit=no"
        />
        {/*
          Disable body scrolling on web to make ScrollView components work correctly.
          If you want to enable scrolling, remove `ScrollViewStyleReset` and
          set `overflow: auto` on the body style below.
        */}
        <ScrollViewStyleReset />
        <style
          dangerouslySetInnerHTML={{
            __html: `
              body {
                background:
                  radial-gradient(circle at top, rgba(120, 76, 79, 0.28), transparent 34%),
                  #120d10;
                display: flex;
                align-items: center;
                justify-content: center;
              }
              body > div:first-child {
                position: fixed !important;
                top: 0;
                left: 0;
                right: 0;
                bottom: 0;
              }
              [role="tablist"] [role="tab"] * { overflow: visible !important; }
              [role="heading"], [role="heading"] * { overflow: visible !important; }

              @media (min-width: 700px) {
                html, body {
                  overflow: hidden !important;
                }
                body > div:first-child {
                  width: 318px !important;
                  max-width: 100vw !important;
                  height: calc(100vh - 72px) !important;
                  max-height: 680px !important;
                  top: 50% !important;
                  bottom: auto !important;
                  left: 50% !important;
                  right: auto !important;
                  transform: translate(-50%, -50%) !important;
                  border-radius: 28px !important;
                  overflow: hidden !important;
                  box-shadow:
                    0 24px 70px rgba(0, 0, 0, 0.45),
                    0 0 0 1px rgba(255,255,255,0.06);
                  background: #0A0A0E;
                }
              }
            `,
          }}
        />
      </head>
      <body
        style={{
          margin: 0,
          height: "100%",
          overflow: "hidden",
          display: "flex",
          flexDirection: "column",
        }}
      >
        {children}
      </body>
    </html>
  );
}
