package com.afmvfcc.controllers;

import java.util.List;

/**
 * Static content for the full AFM VFCC CMS User Guide, rendered with the
 * system's standard letterhead by {@link DocumentExporter#exportUserGuide}.
 * Kept as plain data (title + body lines) so the same content renders to
 * both PDF (iText7) and Word (POI) without duplicating the copy.
 *
 * Convention: a body line starting with "- " is rendered as a bullet;
 * everything else is rendered as a normal paragraph.
 */
public class UserGuideContent {

    public record GuideSection(String heading, List<String> body) {}

    public static List<GuideSection> sections() {
        return List.of(

            new GuideSection("1. Getting Started", List.of(
                "The AFM VFCC Church Management System (CMS) is the desktop application used to manage members, " +
                "attendance, welfare, the church calendar, inventory, communications, the website, and church " +
                "records. It runs on the church office computer and talks to a companion Android app used by " +
                "ushers to take attendance on their phones.",
                "Logging in: enter your username and password on the login screen and press Enter or click Login. " +
                "If you type the wrong password five times in a row, the account is locked and only a Super Admin " +
                "can unlock it again (Admins module, Unlock button).",
                "The sidebar on the left is organised into sections: MAIN (Dashboard, Members, Attendance), " +
                "CHURCH (Church Board, Calendar, Welfare, Finance, Inventory), COMMUNICATIONS (Communications, " +
                "Website, Broadcast), CEREMONIES (Commemorations), and SYSTEM (Admins, Audit Log, Settings, " +
                "Documentation). Click any item to open that module in the main content area; the item you are " +
                "on is highlighted.",
                "Your name and role appear at the bottom of the sidebar, with a Logout button beneath them.",
                "Session timeout: for security, you are automatically logged out after 15 minutes of inactivity. " +
                "Save your work regularly if you plan to step away.",
                "Notifications: whenever you add, edit, delete, or send something anywhere in the system, a small " +
                "\"Success\" (green) or \"Failed\" (red) card briefly appears in the bottom-right corner of the " +
                "window confirming what happened. If an action fails, read this message — it usually explains why."
            )),

            new GuideSection("2. Dashboard", List.of(
                "The Dashboard is the first screen you see after logging in. It gives a live, at-a-glance overview " +
                "of the church's activity and is refreshed every time you open it.",
                "Stat cards along the top show Total Members, Active (Full Time) members, Part Time members, and " +
                "last week's attendance with a trend indicator comparing it to the week before. Click a stat card " +
                "to jump straight to the related module.",
                "Below the stat cards are two charts: a bar chart showing how many members belong to each of the " +
                "main ministries, and a line chart showing attendance over the last six weeks so you can spot " +
                "trends at a glance.",
                "A Welfare Cases by Status chart shows how many welfare cases are Pending, In Progress, or " +
                "Completed, so leadership can see at a glance whether cases are being followed up on.",
                "A Recent Activity feed lists the latest actions taken across the system (who did what, and when), " +
                "pulled from the Audit Log, so you can see what has changed recently without opening Audit Log " +
                "yourself.",
                "The reminders grid shows Upcoming Events in the next 7 days, Birthdays This Week, Welfare Cases " +
                "needing attention, and Pending Member Approvals awaiting review — each with a count badge and a " +
                "shortcut into the relevant module."
            )),

            new GuideSection("3. Members", List.of(
                "The Members module is organised into tabs: All Members, Sub-Branches, Ministries, Families, " +
                "Guests, Pending Review, and Faithful Departed.",
                "All Members — Adding a member: click \"+ Add Member\", fill in the form (full name, date of " +
                "birth, gender, marital status, employment status, contact details, sub-branch, baptism date, " +
                "next of kin, and ministries via the checkboxes), then click Save.",
                "Editing a member: click Edit on their row, change any field, and Save. Deleting a member performs " +
                "a soft delete — the record is hidden from lists but preserved in the database for history.",
                "Use the search bar to find a member by name, phone, or email, and the filter dropdowns to narrow " +
                "the list by status, sub-branch, or ministry. Click \"Export\" to save the current list as a PDF, " +
                "Word document, or Excel file with the church letterhead.",
                "Sub-Branches and Ministries — these two tabs let you maintain the list of sub-branches and " +
                "ministries members can be assigned to. Add, rename, or deactivate them here; deactivating hides " +
                "them from new-member forms without deleting historical assignments.",
                "Families — group related members into a family unit for record-keeping (e.g. so a household's " +
                "attendance and giving history can be viewed together). Create a family, then link members to it " +
                "from their member record.",
                "Guests — every guest recorded by an usher on the Android app (name, phone, gender, sub-branch, " +
                "who invited them, whether they want to join, and any prayer request) appears here automatically " +
                "after the usher syncs. Click View to see the full record, including the prayer request highlighted " +
                "for the Bishop. Click Promote to move a guest into Pending Review for consideration as a full " +
                "member, or Dismiss if no further follow-up is needed.",
                "Pending Review — new member applications, whether promoted from Guests or submitted directly from " +
                "the app, wait here for an admin to Approve (which creates the full member record) or Reject.",
                "Faithful Departed — deceased members are moved here rather than deleted, so their full history is " +
                "preserved. From a member's row in All Members, use \"Record as Faithful Departed\" to set the " +
                "date of passing and an optional memorial note. From this tab you can edit that record or Restore " +
                "a member back to the active list if they were recorded in error."
            )),

            new GuideSection("4. Attendance", List.of(
                "Attendance has two tabs: Take Attendance and Session History.",
                "Creating a session: click \"+ New Session\", give it a name (e.g. \"Sunday Service\"), pick the " +
                "date, and optionally restrict it to one ministry. Click Create Session.",
                "Marking attendance: select the session from the dropdown, then tap a member's row (or its " +
                "checkbox) to toggle them Present or Absent — the row turns green when marked present. Use the " +
                "search bar and the ministry filter to narrow the list, and \"Mark All Present\" / \"Clear All\" " +
                "for quick bulk actions. The present/total count at the top updates live.",
                "Connecting the mobile app: the Server Address box (top right of Take Attendance) shows the IP " +
                "address and a QR code ushers scan from their phone to connect the Android app — see the Mobile " +
                "App section below. Tap the box to enlarge the QR code for easier scanning.",
                "Syncing with the app: click \"Sync with App\" at any time to pull in the latest attendance and " +
                "guest records an usher has synced from their phone, without needing to reselect the session.",
                "Closing a session: once a service is over, click \"Close Session\" to lock it — closed sessions " +
                "can still be viewed but no longer accept new attendance changes, from either the desktop or the " +
                "app.",
                "Exporting: \"Print All\" exports every member (present and absent); \"Print Present\" exports " +
                "only those marked present — both produce a branded PDF/Word/Excel document.",
                "Session History — search past sessions by name or ministry, click View to reopen a session for " +
                "review, Close to lock an open one, or Delete to permanently remove a session and its attendance " +
                "records (this cannot be undone)."
            )),

            new GuideSection("5. Church Board", List.of(
                "Manage the church's board membership and its meetings.",
                "Board Members: add a member to the board with their role/title and a start date; set an end date " +
                "(or deactivate) when their term ends. The list can be searched by name or role.",
                "Meetings: create a meeting with a title, date, location, and a typed agenda. After the meeting, " +
                "fill in the minutes text field with what was discussed and decided. Mark attendees present or " +
                "note an apology for each board member. Meetings default to \"Upcoming\" status and can be marked " +
                "\"Completed\" once held.",
                "Attendee counts and meeting history are searchable, so you can quickly find how a particular " +
                "meeting went or how consistently a board member has attended."
            )),

            new GuideSection("6. Calendar", List.of(
                "The Calendar tracks church events — services, meetings, outreach programmes, youth events, and " +
                "anything else — separately from the public website's event listing.",
                "Click a date to add an event with a title, time, category, location, and description. Use the " +
                "category filter to show only certain types of events.",
                "Past dates are greyed out and locked: you cannot add a new event to a date that has already " +
                "passed, which keeps the calendar an accurate forward-looking record.",
                "Events in the next 7 days automatically appear on the Dashboard's reminders grid so leadership " +
                "sees what's coming up without opening the Calendar directly."
            )),

            new GuideSection("7. Welfare", List.of(
                "Welfare tracks pastoral care and support cases for members who need follow-up — illness, " +
                "bereavement, financial hardship, or other needs.",
                "Opening a case: select the member, enter the reason, and assign a welfare worker (from the list " +
                "of members registered as welfare workers) to follow up. The case starts as \"Pending\".",
                "Following up: the assigned worker updates the Report field with progress notes as they visit or " +
                "check in, and changes the status to \"In Progress\" and eventually \"Completed\" once resolved. " +
                "There is a quick \"Close\" button on the table for marking a case completed in one click.",
                "Cases that are Pending or In Progress appear on the Dashboard's reminders grid and in the Welfare " +
                "Cases by Status chart, so nothing gets forgotten."
            )),

            new GuideSection("8. Inventory", List.of(
                "Inventory keeps track of the church's physical assets: musical instruments, office equipment, " +
                "building materials, furniture, and electronics.",
                "Adding an item: click \"+ Add Item\" and fill in the item name, category, quantity and unit " +
                "(e.g. \"3 pcs\"), condition (New, Good, Fair, Needs Repair, or Damaged), location, the person or " +
                "department responsible (custodian), and optionally the purchase date, purchase value, a low-stock " +
                "alert threshold, and free-text notes.",
                "The summary cards at the top show total items, the estimated total value of everything recorded, " +
                "how many items are at or below their low-stock threshold, and how many need repair or are " +
                "damaged — useful for planning maintenance or replacement budgets.",
                "Use the search bar and the category/condition filters to find items quickly. Editing and deleting " +
                "work the same way as other modules (delete is a soft delete, so history is preserved).",
                "Click \"Export\" to save the current list as an Excel spreadsheet for budgeting, insurance, or " +
                "audit purposes."
            )),

            new GuideSection("9. Communications", List.of(
                "Communications has two tabs: Compose Message and Announcements, plus a Sent Log of everything " +
                "that has gone out.",
                "Compose Message: choose a recipient group (All Active Members, Full Time, Part Time, a specific " +
                "ministry, a specific sub-branch, or the Board), choose the channel (Email via SMTP, or SMS via " +
                "BulkSMS), write the subject (for email) and message, and click Send. A preview screen lists every " +
                "recipient found for that group so you can tick/untick individuals before sending, and shows " +
                "whether each one has a usable email address or phone number on file.",
                "Announcements: post a message to the church's WhatsApp and/or Facebook Page at the same time, " +
                "optionally with an attached image, PDF, Word document, or video. Credentials for these channels " +
                "are configured once in Settings.",
                "Sent Log: every message and announcement sent is recorded here with date, channel, recipients, " +
                "and content, so you can always check what was communicated and to whom.",
                "SMTP, BulkSMS, WhatsApp, and Facebook credentials are entered once in the Settings module, not " +
                "here — Communications only uses them to send."
            )),

            new GuideSection("10. Website", List.of(
                "Manage the content shown on the church's public website: blog posts and website events (separate " +
                "from the internal Calendar).",
                "Blog posts: click to add a title, author, and content, then Publish. Editing and deleting work " +
                "the same way as other modules.",
                "Website events: add a title, date, description, and an optional poster image, which is uploaded " +
                "alongside the event.",
                "Publishing: every time you save a blog or event, the website's data file is regenerated " +
                "automatically and, if configured, pushed straight to the church's GitHub repository (and, " +
                "optionally, a local website folder as well) — see the Settings section for how to connect these. " +
                "There is nothing further to do; the public website updates within a few seconds."
            )),

            new GuideSection("11. Broadcast", List.of(
                "Broadcast automates turning a Facebook Live recording into a YouTube upload.",
                "Paste the Facebook video/Live URL, give it a title and description, and start the download — the " +
                "system downloads the video, then uploads it to the church's YouTube channel automatically once " +
                "the download finishes.",
                "The first time you use this, you'll need to authorise the church's YouTube account (a one-time " +
                "Google sign-in) and upload Google API credentials in Settings — the module will prompt you if " +
                "this hasn't been done yet.",
                "Upload history shows the status of every download/upload with a direct link to the resulting " +
                "YouTube video once it's live. Downloaded video files are deleted automatically from the computer " +
                "after a successful upload to save disk space."
            )),

            new GuideSection("12. Commemorations", List.of(
                "Issue printable certificates for Baptism, Blessing, Certificate of Appreciation, and Death " +
                "Announcement.",
                "Choose the certificate type, fill in the relevant details (recipient name, date, and any " +
                "type-specific fields such as bride/groom names for a blessing certificate), then Preview to see " +
                "exactly how it will print, and Save to export the finished, professionally formatted certificate " +
                "as a PDF."
            )),

            new GuideSection("13. Admins", List.of(
                "This module is only visible to the Super Admin and manages every login account in the system — " +
                "both desktop Admins and mobile-only Ushers.",
                "Adding an account: click \"+ Add Admin\", fill in the full name, username, a password, optional " +
                "email/phone/role title, and choose the Account Type:",
                "- Admin — full access to the desktop CMS and the mobile app.",
                "- Usher — mobile app only (attendance and guest recording). Ushers are blocked from logging into " +
                "the desktop CMS with a clear message, so this is the right choice for volunteers who only need " +
                "to help with attendance on Sundays.",
                "Editing an account works the same way; leave the password field blank to keep the existing " +
                "password unchanged.",
                "Unlock: if an account has been locked out after 5 failed login attempts (desktop or mobile app), " +
                "click Unlock next to their name to restore access immediately.",
                "Delete permanently removes an account (not available for the Super Admin account, which cannot " +
                "be deleted or demoted)."
            )),

            new GuideSection("14. Audit Log", List.of(
                "A read-only, Super-Admin-only record of the last 500 admin actions across the system — logins, " +
                "record changes, deletions, sends, and more — each with who performed it and when.",
                "Use the search bar to find actions by admin name or by keywords in the action text (e.g. " +
                "\"deleted\", \"Usher\", or a specific member's name). This is the first place to check if " +
                "something in the system looks like it changed unexpectedly."
            )),

            new GuideSection("15. Settings", List.of(
                "Settings is where all system-wide configuration lives, grouped into cards.",
                "Backup: export the full database to a file at any time, or restore from a previous backup " +
                "(restoring replaces all current data, so use with care). A history of recent backups is kept.",
                "Email (SMTP): host, port, and login details for the email account used to send member " +
                "communications.",
                "SMS (BulkSMS): API token and sender ID for sending SMS through BulkSMS South Africa.",
                "Announcements: WhatsApp Cloud API and Facebook Page API credentials used by the Announcements " +
                "feature in Communications.",
                "Broadcast: where downloaded Facebook videos are temporarily stored, and the Google API " +
                "credentials/authorisation used to upload to YouTube.",
                "GitHub Integration and Website: connect the church's GitHub repository (and, optionally, a local " +
                "website folder) so that saving a blog post or event in the Website module publishes automatically.",
                "Mobile App Connection: shows the address and QR code ushers use to connect the Android app to " +
                "this computer, automatically preferring the Windows Mobile Hotspot adapter when it is turned on. " +
                "Ushers turn Mobile Hotspot on themselves in Windows Settings — the CMS only displays the " +
                "connection details, it does not turn the hotspot on for you.",
                "Connected Devices: lists every phone currently signed in to the Android app, showing whether each " +
                "is an Admin or Usher account. Click Revoke to immediately sign a device out, for example if a " +
                "phone is lost or an usher's involvement has ended."
            )),

            new GuideSection("16. Mobile App (Android)", List.of(
                "Ushers and other assigned church workers use the AFM VFCC Android app to take attendance and " +
                "record guests from their phone during services, then sync everything back to the desktop CMS.",
                "Connecting: open the app, tap Server Settings, then \"Scan QR Code\" and point the camera at the " +
                "QR code shown on the CMS's Settings or Attendance screen — the server address fills in " +
                "automatically. If a camera isn't available, the address can be typed in manually instead.",
                "Logging in: use the same username and password as a desktop admin account, or the credentials of " +
                "a dedicated Usher account created for you in Admins.",
                "Taking attendance: pick today's session (or create a new one from the app), then tap each " +
                "member's row to mark them present — tap again to unmark. Use the search bar to find someone " +
                "quickly, and the menu button (top right) for Mark All Present or Clear All.",
                "Adding a guest: tap Add Guest, fill in their details (name, phone, gender, sub-branch, who " +
                "invited them, whether they're interested in membership, and any prayer request), and Save. " +
                "Guests are recorded against the session you're currently in, so switching to a different session " +
                "shows only that session's guests.",
                "Syncing: tap \"Sync\" to send attendance and guest records to the CMS. The phone must be " +
                "connected to the church WiFi (or the CMS computer's Mobile Hotspot) to sync — if you're offline, " +
                "records stay safely on the phone until you can sync later."
            )),

            new GuideSection("17. Troubleshooting & FAQ", List.of(
                "Forgot password / account locked: contact the Super Admin to reset your password or unlock your " +
                "account from the Admins module.",
                "Mobile app won't connect: make sure the phone and the CMS computer are on the same WiFi network " +
                "(or the phone is connected to the CMS computer's Mobile Hotspot), then re-scan the QR code shown " +
                "in the CMS's Settings or Attendance screen — the address changes whenever the network changes.",
                "A record I just added/edited isn't showing up: check the search box and any active filters aren't " +
                "hiding it, and click any \"Refresh\"/\"Sync\" button on that screen if one is present.",
                "Something looks wrong or was changed unexpectedly: check the Audit Log (Super Admin only) to see " +
                "exactly who did what and when.",
                "Website not updating: confirm the GitHub token/owner/repository details in Settings > GitHub " +
                "Integration are correct and click \"Test Connection\".",
                "For anything not covered here, contact the system administrator or the developer who set up the " +
                "system for your church."
            ))
        );
    }
}
