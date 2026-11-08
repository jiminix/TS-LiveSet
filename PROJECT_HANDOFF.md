# Meryl TS — Playlist Live / LiveSet — dossier de reprise

> Source de vérité pour reprendre le développement depuis un nouveau chat, appareil ou compte ChatGPT.
> Dépôt : https://github.com/jiminix/TS-LiveSet
> Branche de référence observée : main
> Dernière vérification documentaire : 2026-11-08.

## État connu et limites
- Le fichier VERSION indiquait **LiveSet V0.87** et **Viewer V0.28** lors de la vérification du 2026-11-08.
- Le README indique encore « LiveSet V0.2 » : il est donc obsolète pour les numéros de version.
- Ces numéros ne constituent **pas** des SHA de commit. Toujours relever les SHA actuels avant de modifier le code.
- Ne pas déduire l'état des fichiers ou le succès des builds des seules versions affichées.
- Ne jamais inventer les décisions ou changements des conversations précédentes non documentées.

## Fonctionnalités annoncées dans README
- Bibliothèque de chansons et recherche
- Plusieurs setlists, ajout/retrait/réorganisation
- Édition des paroles et des informations de chansons
- Réorganisation des paroles en sections
- Mode Live avec grand texte et écran maintenu éveillé
- Lecteur YouTube/web intégré
- Sauvegarde locale sur téléphone
- Import en masse Google Docs, Google Sheets TSV, TXT/CSV/TSV, DOCX
- Aperçu d'import, protection contre les doublons, création facultative de setlist

## Construction
Selon le README : GitHub Actions génère `app-debug.apk` lors des pushes sur `main`, ou via Actions > Build LiveSet APK > Run workflow. Vérifier l'état réel des workflows et les journaux avant de déclarer un APK opérationnel.

## Procédure obligatoire de reprise
1. Ouvrir ce fichier et `VERSION`, puis lire le code et les workflows pertinents dans le dépôt.
2. Identifier la branche par défaut, le dernier commit HEAD (SHA complet), les fichiers touchés et l'état des derniers builds.
3. Vérifier les changements déjà présents avant de proposer des modifications.
4. Pour chaque changement : résumer les demandes, modifier le minimum nécessaire, conserver les fonctions existantes, vérifier les impacts et lancer/contrôler les tests disponibles.
5. Noter après chaque modification : date, version LiveSet/Viewer, branche, SHA avant/après, fichiers modifiés, résultat du build, anomalies restantes.
6. Ne jamais prétendre avoir testé l'APK sur un téléphone si ce test n'a pas été réalisé.

## Journal de reprise
### 2026-11-08
- Reprise du projet depuis https://github.com/jiminix/TS-LiveSet.
- Lecture via connexion GitHub des fichiers README.md et VERSION.
- Versions observées : LiveSet V0.87 / Viewer V0.28.
- Création du présent dossier de reprise pour rendre le contexte technique durable.
- Les derniers SHA HEAD et état de build restent à vérifier.

## Sécurité, accès et autorisations
- Les droits d'accès GitHub dépendent du compte GitHub, des permissions du dépôt et de la connexion/autorisation du compte ChatGPT utilisé.
- Un changement de chat avec la même connexion peut permettre de relire ce fichier ; un autre compte ChatGPT devra connecter GitHub avec les droits nécessaires.
- Ne jamais stocker ici de token GitHub, clé API, mot de passe, secret, certificat de signature Android ou donnée personnelle.
- Un SHA est un identifiant de contenu ou de commit, pas un droit d'accès ni une autorisation.
- Avant toute opération destructrice (suppression, réécriture d'historique, remplacement important), demander confirmation explicite.

## Message de reprise à copier dans un nouveau chat
« Reprends mon projet Meryl TS — Playlist Live dans le dépôt GitHub jiminix/TS-LiveSet. Commence par lire PROJECT_HANDOFF.md, VERSION, README.md et les fichiers de code concernés. Vérifie HEAD, SHA, branche et dernier build avant toute modification. Préserve les fonctionnalités existantes. Mets à jour le dossier de reprise après chaque changement important. »
