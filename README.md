# Polso

Dashboard Android personale per i dati della HUAWEI Band 11, letti da Connessione Salute
(Huawei Health → Health Sync → Connessione Salute).

Mostra: passi ora per ora (24 barre orizzontali), totale, km e calorie; passi degli ultimi
7 giorni; battito della giornata con min/media/max e andamento del battito a riposo; sonno con
ipnogramma e fasi; HRV; SpO₂; frequenza respiratoria; allenamenti. Le sezioni senza dati non
compaiono. Lo stress non è disponibile perché Connessione Salute non prevede quel tipo di dato.

## Installazione dal telefono

1. Apri la pagina **Releases** del repository e scarica `Polso.apk` dall'ultima versione.
2. Aprilo: Android chiederà di consentire l'installazione da questa fonte (browser o file manager).
3. Avvia Polso e tocca **Concedi accesso**: scegli i dati da condividere in Connessione Salute.

Ogni modifica al codice sul ramo `main` produce automaticamente una nuova release
(GitHub Actions). Gli aggiornamenti si installano sopra la versione precedente
perché la chiave di firma (`app/debug.keystore`) è fissa.
