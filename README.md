
# wias

## Installation Instructions

* run `mvn clean install`
* copy jar to ~/.mycore/(dev-)mir/lib/

## Development

You can add these to your ~/.mycore/(dev-)mir/.mycore.properties
```
MCR.Developer.Resource.Override=/path/to/reposis_wias/src/main/resources
MCR.LayoutService.LastModifiedCheckPeriod=0
MCR.UseXSLTemplateCache=false
MCR.SASS.DeveloperMode=true
```

## DOI-Generierung (WIAS-20)

### Übersicht

DOIs werden automatisch anhand des Genres und der verknüpften Serie generiert. Jede DOI-Art ist an eine konfigurierte Serie gebunden — ein Report ohne passende Serie bekommt keine DOI.

### DOI-Muster

| Genre            | Serie                  | DOI-Muster                        | ID-Quelle              |
|------------------|------------------------|-----------------------------------|------------------------|
| Preprint         | `PreprintSeriesId`     | `10.20347/WIAS.PREPRINT.<Volume>` | Volume aus relatedItem |
| Research Data    | —                      | `10.20347/WIAS.DATA.<N>`          | Auto-Counter           |
| Software         | —                      | `10.20347/WIAS.SOFTWARE.<N>`      | Auto-Counter           |
| Report           | `ReportSeriesId`       | `10.20347/WIAS.REPORT.<Volume>`   | Volume aus relatedItem |
| Technical Report | `TechReportSeriesId`   | `10.20347/WIAS.TECHREPORT.<N>`    | Auto-Counter           |
| Annual Report    | `AnnualReportSeriesId` | `10.20347/WIAS.ARR.<YY>000`       | Jahr aus dateIssued    |
| Article (in AR)  | `AnnualReportSeriesId` | `10.20347/WIAS.ARR.<YY><NNN>`     | Jahr + Counter         |

### Konfiguration

```properties
# DOI-Prefix
MCR.PI.Generator.DOIGenerator.Prefix=10.20347

# Serien-IDs (alle DOI-Arten sind an ihre Serie gebunden)
MCR.PI.Generator.DOIGenerator.PreprintSeriesId=wias_mods_00000012
MCR.PI.Generator.DOIGenerator.AnnualReportSeriesId=wias_mods_00000034
MCR.PI.Generator.DOIGenerator.TechReportSeriesId=wias_mods_00000035
MCR.PI.Generator.DOIGenerator.ReportSeriesId=wias_mods_00000021

# PostProcessor (referenziert PreprintSeriesId per %...%)
WIAS.PostProcessor.PreprintSeriesId=%MCR.PI.Generator.DOIGenerator.PreprintSeriesId%

# Strategy (DOI-Button im UI ein-/ausblenden)
WIAS.Strategy.DOIServiceId=Datacite
```

### Komponenten

#### WiasDOIGenerator
Erzeugt die DOI-Strings. Dispatch über Genre → Serie → Muster. Counter werden aus der PI-Datenbank gelesen und im Speicher inkrementiert.

#### WiasStrategy
Erweitert `MIRStrategy` und blendet den DOI-Registrierungs-Button aus, wenn die Metadaten-Voraussetzungen nicht erfüllt sind (z.B. kein Volume bei Preprints, falsche Serie bei Reports).

#### WiasPostProcessor
Erweitert `MIRPostProcessor` und wird beim Speichern im Editor ausgeführt:

- **Volume-Generierung**: Vergibt automatisch die nächste freie Preprint-Nummer wenn:
  1. Die Serie die Preprint-Serie ist
  2. Noch kein Volume gesetzt ist
  3. Der Nutzer die Rolle `editor` oder `admin` hat
  4. Das Attribut `@generate-volume="true"` auf dem `relatedItem` gesetzt ist (Checkbox im Editor)
- **Unauthorized-Volume-Strip**: Entfernt ein manuell eingetragenes Volume bei der Preprint-Serie wenn der Nutzer kein Editor/Admin ist
- **Cleanup**: Entfernt immer das `@generate-volume`-Attribut vom `relatedItem`, da es kein gültiges MODS ist

#### WiasPreprintNumberEventHandler
Synchronisiert die Tabelle `WIAS_PREPRINT_NUMBER` bei Create/Update/Delete von Objekten mit der Preprint-Serie.

### Editor-Anpassungen

#### Admin-Editor (`host.volume`-Template in `editor-customization.xed`)
Das Template wird überschrieben und unterscheidet per `xed:if` anhand der `@xlink:href`:

- **Preprint-Serie**: Volume-Feld ist `readonly`. Zusätzlich wird eine Checkbox "Volume automatisch vergeben" angezeigt, die `@generate-volume` auf dem `relatedItem` setzt. Das Attribut liegt bewusst auf dem `relatedItem` (nicht auf `mods:detail`), da die XEditor-Cleanup-Rules leere `mods:part`-Bäume entfernen würden.
- **Andere Serien**: Normales editierbares Volume-Feld.

#### Normaler Editor (Serie-Suche in `hosts.series`)
Per JavaScript (`wias.js`) wird das Volume-Feld nach Auswahl der Preprint-Serie geleert und disabled. Die Logik wraps die globale `fillFieldset`-Funktion aus `relatedItem-autocomplete.js`.

#### JavaScript-Konfiguration
Serien-IDs werden nicht hardkodiert, sondern via `mir-flatmir-layout.xsl` aus den Properties in `window.wiasConfig` geschrieben:

```javascript
window["wiasConfig"] = {
  preprintSeriesId: "...",
  techReportSeriesId: "...",
  reportSeriesId: "..."
};
```