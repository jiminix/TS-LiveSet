# Reprise Manager V1.05 — 2026-11-09

Branche de travail : `manager-v105-safe-migration`. Branche principale `main` inchangée.

## Objectif
Installer une nouvelle application V1.05 à côté de la V1.04 existante, sans écraser les playlists, et mettre en place une signature durable.

## Changements déjà faits
- `app/build.gradle` : `applicationId com.jiminix.liveset.v105`, versionCode 105, versionName 1.05.
- `app/src/main/AndroidManifest.xml` : libellé V1.05, permission et provider différenciés.
- `PlaylistCloudSync.maybePublish` : désactivation temporaire de la publication automatique sur V1.05, pour éviter d'écraser la sauvegarde cloud de V1.04.

## Points non terminés
- La V1.05 n'est PAS compilée ni signée.
- Vérifier et restaurer la sauvegarde V1.04 avant installation.
- Générer une nouvelle clé privée durable hors dépôt public, conserver une sauvegarde sécurisée et configurer les secrets GitHub.
- Créer un workflow V1.05 autonome, vérifier la compilation et l'empreinte.
- Vérifier les interactions avec le Viewer et les autorités Android ; ne pas supposer que la migration cloud fonctionne sans test.
- Conserver la V1.04 installée jusqu'à validation complète.

## Ancienne signature
Le workflow de migration mentionne l'empreinte de la V1.04 :
`C3442E5EEF079FE39ECE2DA2EB158799151638AAE35093E97BBA2F270A9A9321`.
L'ancienne clé privée n'a pas été récupérée.

## Sécurité
Ne jamais committer de keystore ou secret. Ne pas générer une clé de remplacement pour une mise à jour directe de V1.04. Ne pas désinstaller V1.04 sans export/restauration vérifiés.

## Avancement du 2026-11-09
- V1.05 reste sur la branche dédiée.
- Nouvelle garde dans PlaylistCloudSync : impossible de sauvegarder vers le cloud depuis une installation V1.05 sans chansons ou sans playlists.
- Workflow `.github/workflows/build-manager-v105.yml` créé : compilation sur demande, clé de signature obligatoire via secret `MANAGER_V105_KEYSTORE_BASE64`, empreinte attendue via variable `MANAGER_V105_CERT_SHA256`, aucun renouvellement automatique de clé.
- Il faut créer et conserver une nouvelle clé durable et renseigner ces paramètres avant de produire un APK. Le build n'a pas encore été validé.
- Le fichier de reprise Android ne remplace PAS la sauvegarde des playlists.
