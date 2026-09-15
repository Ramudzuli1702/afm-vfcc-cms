package com.afmvfcc.controllers;

import java.util.List;

/**
 * Static content for the plain, letterhead-free System Usage Policy,
 * rendered by {@link DocumentExporter#exportPolicy}.
 *
 * Convention: a body line starting with "- " is rendered as a bullet;
 * everything else is rendered as a normal paragraph.
 */
public class PolicyContent {

    public record PolicySection(String heading, List<String> body) {}

    public static List<PolicySection> sections() {
        return List.of(

            new PolicySection("1. Purpose and Scope", List.of(
                "This policy governs the use of the AFM VFCC Church Management System (\"the System\"), including " +
                "the desktop application and its companion Android mobile app, by all administrators, ushers, and " +
                "other authorised users. It applies to all data stored, processed, or transmitted by the System, " +
                "including member records, attendance, welfare case notes, financial and inventory records, and " +
                "communications sent through it."
            )),

            new PolicySection("2. Definitions", List.of(
                "- \"Admin\" — a user account with full access to the desktop System and the mobile app.",
                "- \"Usher\" — a user account restricted to the mobile app only (attendance and guest recording), " +
                "with no access to the desktop System.",
                "- \"Super Admin\" — the single account with full administrative authority, including the ability " +
                "to create, edit, and unlock all other accounts.",
                "- \"Member Data\" — any personal information about a church member or guest recorded in the " +
                "System, including but not limited to contact details, family information, welfare notes, and " +
                "prayer requests.",
                "- \"System\" — the AFM VFCC Church Management System desktop application and its Android " +
                "companion app, together."
            )),

            new PolicySection("3. Access Levels and Accountability", List.of(
                "Access to the System is granted only through an individually assigned account. Shared or " +
                "generic login accounts are not permitted.",
                "Each user is accountable for all actions performed under their account. All significant actions " +
                "are automatically recorded in the System's Audit Log, including who performed the action and " +
                "when.",
                "Usher accounts exist specifically to allow attendance-taking volunteers to assist on Sundays and " +
                "at events without being granted access to the full administrative system. Admins must not share " +
                "their own credentials with ushers or volunteers under any circumstances — a dedicated Usher " +
                "account must be created instead.",
                "Only the Super Admin may create, edit, unlock, or deactivate other accounts."
            )),

            new PolicySection("4. Account and Password Security", List.of(
                "Users must choose a password that is not easily guessed and must not share it with anyone else, " +
                "including other church staff or family members.",
                "Passwords must never be written down in an unsecured location or sent over plain email/SMS/chat.",
                "If a user suspects their account has been compromised, they must notify the Super Admin " +
                "immediately so the password can be reset.",
                "The System automatically locks an account after five consecutive failed login attempts, on both " +
                "the desktop and the mobile app, as a protection against unauthorised access attempts. Only the " +
                "Super Admin can unlock a locked account."
            )),

            new PolicySection("5. Acceptable Use", List.of(
                "The System is provided for official church administrative purposes only — managing members, " +
                "attendance, welfare, communications, events, inventory, and related church business.",
                "Users must not use the System to access, copy, or share Member Data for any purpose unrelated to " +
                "their official duties.",
                "Users must not attempt to access modules, records, or accounts beyond what is necessary for their " +
                "assigned role.",
                "Devices used to access the System (the office computer and any phone running the mobile app) must " +
                "not be left logged in and unattended in a place accessible to the public."
            )),

            new PolicySection("6. Data Privacy and Confidentiality", List.of(
                "Member Data, including welfare case notes and prayer requests, is confidential. It must only be " +
                "viewed, discussed, or acted upon by those with a legitimate pastoral or administrative reason to " +
                "do so.",
                "Prayer requests and welfare information recorded through the mobile app are intended for review " +
                "by church leadership (the Bishop and assigned welfare workers) and must not be disclosed or " +
                "discussed outside that context without the member's consent.",
                "Exported documents (PDF, Word, or Excel files containing Member Data) must be stored securely and " +
                "deleted once they are no longer needed for their original purpose."
            )),

            new PolicySection("7. Data Retention and Backup", List.of(
                "The database should be backed up regularly using the System's built-in Backup feature (Settings " +
                "> Backup). The Super Admin is responsible for ensuring backups are taken on a reasonable schedule " +
                "and stored safely, separate from the office computer where practical.",
                "Deleted member, guest, and inventory records are retained in the database (soft-deleted, hidden " +
                "from normal views) rather than permanently erased, to preserve historical church records. " +
                "Permanent deletion (where offered by the System) should only be used for genuine data-entry " +
                "errors, not routine record-keeping.",
                "Restoring a backup replaces all current data in the System and should only be performed by the " +
                "Super Admin, with a clear understanding that any changes made since that backup was taken will be " +
                "lost."
            )),

            new PolicySection("8. Mobile App and Network Use", List.of(
                "The mobile app communicates with the office computer only over the local church WiFi network or " +
                "the office computer's Mobile Hotspot — it does not send data over the public internet. Ushers " +
                "should only connect using a network/hotspot they know to be the church's own.",
                "A phone with the mobile app installed and signed in should not be left unattended and unlocked, " +
                "and should be signed out (or have its session revoked from Settings > Connected Devices) if it is " +
                "lost, stolen, or the usher's involvement with attendance-taking ends."
            )),

            new PolicySection("9. Incident Reporting", List.of(
                "Any of the following must be reported to the Super Admin as soon as possible: a lost or stolen " +
                "device that was signed in to the System, a suspected compromised account, data that appears to " +
                "have been changed or deleted without explanation, or any other suspected misuse of the System.",
                "On receiving such a report, the Super Admin should revoke the affected device from Settings > " +
                "Connected Devices, reset the affected account's password, review the Audit Log for the relevant " +
                "period, and take any further action needed."
            )),

            new PolicySection("10. Monitoring and Enforcement", List.of(
                "Use of the System is subject to monitoring through the Audit Log, which records account activity " +
                "for accountability and security purposes.",
                "Violation of this policy may result in the suspension or removal of System access, at the " +
                "discretion of church leadership, and in serious cases (such as deliberate misuse of confidential " +
                "member information) may be treated as a disciplinary matter."
            )),

            new PolicySection("11. Policy Review", List.of(
                "This policy should be reviewed periodically by church leadership and updated as the System's " +
                "features change, or as needed to reflect the church's current practices."
            )),

            new PolicySection("12. Acknowledgement", List.of(
                "By being issued a System account, a user acknowledges that they have read, understood, and agree " +
                "to comply with this policy.",
                "Name: _______________________________",
                "Role: _______________________________",
                "Signature: ___________________________          Date: ______________"
            ))
        );
    }
}
