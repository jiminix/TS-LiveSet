# LiveSet V0.2
Android songbook / setlist app for live performance.

## Features
- Song library with search
- Multiple setlists
- Add/remove/reorder songs
- Editable lyrics and song details
- Rearrange lyrics by sections separated with a blank line
- Live mode with large text and screen kept awake
- Embedded YouTube or web player inside the app
- Local persistence on the phone
- Bulk import from Google Docs text, Google Sheets TSV, TXT/CSV/TSV and DOCX
- Import preview, duplicate protection and optional automatic setlist creation

## Bulk import
From the Library, tap **Importer**.
You can paste the whole text copied from Google Docs, or select a TXT/CSV/TSV/DOCX file from Google Drive.
Best detection is obtained when song names use Google Docs heading styles, `Titre: ...`, Markdown headings (`# Song`), or `---` separators.

## Build
GitHub Actions builds `app-debug.apk` automatically on pushes to `main`, or manually via Actions > Build LiveSet APK > Run workflow.
