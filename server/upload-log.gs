/**
 * Pilot Gap log receiver — a Google Apps Script web app that saves trip logs
 * sent by phones into a folder in Ryan's Google Drive.
 *
 * Set-up (once, about 10 minutes): see README.md, "Automatic log upload".
 * Deploy as: Web app, Execute as: Me, Who has access: Anyone.
 *
 * Each phone sends its whole log each time (at End trip and every 10 minutes),
 * so the file for a trip is simply replaced with the newest copy. Sending the same
 * log twice does no harm.
 */

var FOLDER_NAME = 'Pilot Gap logs';
var UPLOAD_KEY  = 'bhe-pg-7Kq2vX9m';   // must match UPLOAD_KEY in index.html; stops random posts, it is not a password
var MAX_BYTES   = 5 * 1024 * 1024;     // refuse anything over 5 MB

function doPost(e) {
  try {
    var body = JSON.parse(e.postData.contents);
    if (body.key !== UPLOAD_KEY) return reply_({ ok: false, error: 'bad key' });
    var name = String(body.name || '');
    if (!/^pilotgap-[A-Za-z0-9._-]{5,120}\.txt$/.test(name)) return reply_({ ok: false, error: 'bad file name' });
    var text = String(body.text || '');
    if (!text || text.length > MAX_BYTES) return reply_({ ok: false, error: 'empty or too large' });

    var folder = folder_();
    var files = folder.getFilesByName(name);
    if (files.hasNext()) {
      files.next().setContent(text);
    } else {
      folder.createFile(name, text, MimeType.PLAIN_TEXT);
    }
    return reply_({ ok: true, saved: name, bytes: text.length, at: new Date().toISOString() });
  } catch (err) {
    return reply_({ ok: false, error: String(err) });
  }
}

// Opening the web app address in a browser shows this, to check it is deployed.
function doGet() {
  return reply_({ ok: true, service: 'Pilot Gap log receiver', folder: FOLDER_NAME });
}

function folder_() {
  var it = DriveApp.getFoldersByName(FOLDER_NAME);
  return it.hasNext() ? it.next() : DriveApp.createFolder(FOLDER_NAME);
}

function reply_(obj) {
  return ContentService.createTextOutput(JSON.stringify(obj)).setMimeType(ContentService.MimeType.JSON);
}
