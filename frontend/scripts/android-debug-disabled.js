#!/usr/bin/env node

console.error("");
console.error("Stone Android: el modo debug esta bloqueado.");
console.error("Usa uno de estos flujos:");
console.error("- npm run android:release");
console.error("- npm run android:install");
console.error("");
console.error("Motivo: las builds debug vuelven a provocar el error rojo de Metro.");
process.exit(1);
