/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.contacts.editor;

import android.test.AndroidTestCase;
import android.test.MoreAsserts;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.contacts.model.account.AccountType;
import com.android.contacts.model.account.AccountWithDataSet;
import com.android.contacts.test.mocks.MockAccountTypeManager;

import com.google.common.collect.Sets;

import java.util.Collection;
import java.util.Set;

/**
 * Test case for {@link ContactEditorUtils}.
 *
 * adb shell am instrument -w -e class com.android.contacts.editor.ContactEditorUtilsTest \
       com.android.contacts.tests/android.test.InstrumentationTestRunner
 */
@SmallTest
public class ContactEditorUtilsTest extends AndroidTestCase {
    private MockAccountTypeManager mAccountTypes;
    private ContactEditorUtils mTarget;

    private static final MockAccountType TYPE1 = new MockAccountType("type1", null, true);
    private static final MockAccountType TYPE2 = new MockAccountType("type2", null, true);
    private static final MockAccountType TYPE2EX = new MockAccountType("type2", "ext", true);

    // Only type 3 is "readonly".
    private static final MockAccountType TYPE3 = new MockAccountType("type3", null, false);

    private static final AccountWithDataSet ACCOUNT_1_A = new AccountWithDataSet(
            "a", TYPE1.accountType, TYPE1.dataSet);
    private static final AccountWithDataSet ACCOUNT_1_B = new AccountWithDataSet(
            "b", TYPE1.accountType, TYPE1.dataSet);

    private static final AccountWithDataSet ACCOUNT_2_A = new AccountWithDataSet(
            "a", TYPE2.accountType, TYPE2.dataSet);
    private static final AccountWithDataSet ACCOUNT_2EX_A = new AccountWithDataSet(
            "a", TYPE2EX.accountType, TYPE2EX.dataSet);

    private static final AccountWithDataSet ACCOUNT_3_C = new AccountWithDataSet(
            "c", TYPE3.accountType, TYPE3.dataSet);

    @Override
    protected void setUp() throws Exception {
        // Initialize with 0 types, 0 accounts.
        mAccountTypes = new MockAccountTypeManager(new AccountType[] {},
                new AccountWithDataSet[] {});
        mTarget = new ContactEditorUtils(getContext(), mAccountTypes);

        // Clear the preferences.
        mTarget.cleanupForTest();
    }

    private void setAccountTypes(AccountType... types) {
        mAccountTypes.mTypes = types;
    }

    private void setAccounts(AccountWithDataSet... accounts) {
        mAccountTypes.mAccounts = accounts;
    }

    public void testGetWritableAccountTypeStrings() {
        String[] types;

        // 0 writable types
        setAccountTypes();

        types = mTarget.getWritableAccountTypeStrings();
        MoreAsserts.assertEquals(types, new String[0]);

        // 1 writable type
        setAccountTypes(TYPE1);

        types = mTarget.getWritableAccountTypeStrings();
        MoreAsserts.assertEquals(Sets.newHashSet(TYPE1.accountType), Sets.newHashSet(types));

        // 2 writable types
        setAccountTypes(TYPE1, TYPE2EX);

        types = mTarget.getWritableAccountTypeStrings();
        MoreAsserts.assertEquals(Sets.newHashSet(TYPE1.accountType, TYPE2EX.accountType),
                Sets.newHashSet(types));

        // 3 writable types + 1 readonly type
        setAccountTypes(TYPE1, TYPE2, TYPE2EX, TYPE3);

        types = mTarget.getWritableAccountTypeStrings();
        MoreAsserts.assertEquals(
                Sets.newHashSet(TYPE1.accountType, TYPE2.accountType, TYPE2EX.accountType),
                Sets.newHashSet(types));
    }

    /**
     * Test for
     * - {@link ContactEditorUtils#saveDefaultAccount}
     * - {@link ContactEditorUtils#getOnlyOrDefaultAccount}
     */
    public void testSaveDefaultAccount() {
        // Use these account types here.
        setAccountTypes(TYPE1, TYPE2);

        mTarget.saveDefaultAccount(null);
        assertNull(mTarget.getOnlyOrDefaultAccount());

        mTarget.saveDefaultAccount(ACCOUNT_1_A);
        assertEquals(ACCOUNT_1_A, mTarget.getOnlyOrDefaultAccount());
    }

    /**
     * Tests for {@link ContactEditorUtils#shouldShowAccountChangedNotification()}, starting with
     * 0 accounts.
     */
    public void testShouldShowAccountChangedNotification_0Accounts() {
        setAccountTypes(TYPE1);

        // First launch -- always true.
        assertTrue(mTarget.shouldShowAccountChangedNotification());

        // We show the notification here, and user clicked "add account"
        setAccounts(ACCOUNT_1_A);

        // Now we open the contact editor with the new account.

        // When closing the editor, we save the default account.
        mTarget.saveDefaultAccount(ACCOUNT_1_A);

        // Next time the user creates a contact, we don't show the notification.
        assertFalse(mTarget.shouldShowAccountChangedNotification());

        // User added a new writable account, ACCOUNT_1_B.
        setAccounts(ACCOUNT_1_A, ACCOUNT_1_B);

        // Since default account is still ACCOUNT_1_A, we don't show the notification.
        assertFalse(mTarget.shouldShowAccountChangedNotification());

        // User saved a new contact.  We update the account list and the default account.
        mTarget.saveDefaultAccount(ACCOUNT_1_B);

        // User created another contact.  Now we don't show the notification.
        assertFalse(mTarget.shouldShowAccountChangedNotification());

        // User installed a new contact sync adapter...

        // Added a new account type: TYPE2, and the TYPE2EX extension.
        setAccountTypes(TYPE1, TYPE2, TYPE2EX);
        // Add new accounts: ACCOUNT_2_A, ACCOUNT_2EX_A.
        setAccounts(ACCOUNT_1_A, ACCOUNT_1_B, ACCOUNT_2_A, ACCOUNT_2EX_A);

        // New added account but default account is still not changed, so no notification.
        assertFalse(mTarget.shouldShowAccountChangedNotification());

        // User saves a new contact, with a different default account.
        mTarget.saveDefaultAccount(ACCOUNT_2_A);

        // Next time user creates a contact, no notification.
        assertFalse(mTarget.shouldShowAccountChangedNotification());

        // Remove ACCOUNT_2EX_A.
        setAccountTypes(TYPE1, TYPE2, TYPE2EX);
        setAccounts(ACCOUNT_1_A, ACCOUNT_1_B, ACCOUNT_2_A);

        // ACCOUNT_2EX_A was not default, so no notification either.
        assertFalse(mTarget.shouldShowAccountChangedNotification());

        // Remove ACCOUNT_1_B, which is default.
        setAccountTypes(TYPE1, TYPE2, TYPE2EX);
        setAccounts(ACCOUNT_1_A, ACCOUNT_1_B);

        // Now we show the notification.
        assertTrue(mTarget.shouldShowAccountChangedNotification());

        // Do not save the default account, and add a new account now.
        setAccountTypes(TYPE1, TYPE2, TYPE2EX);
        setAccounts(ACCOUNT_1_A, ACCOUNT_1_B, ACCOUNT_2EX_A);

        // No default account, so show notification.
        assertTrue(mTarget.shouldShowAccountChangedNotification());
    }

    /**
     * Tests for {@link ContactEditorUtils#shouldShowAccountChangedNotification()}, starting with
     * 1 accounts.
     */
    public void testShouldShowAccountChangedNotification_1Account() {
        setAccountTypes(TYPE1, TYPE2);
        setAccounts(ACCOUNT_1_A);

        // Always returns false when 1 writable account.
        assertFalse(mTarget.shouldShowAccountChangedNotification());

        // User saves a new contact.
        mTarget.saveDefaultAccount(ACCOUNT_1_A);

        // Next time, no notification.
        assertFalse(mTarget.shouldShowAccountChangedNotification());

        // The rest is the same...
    }

    /**
     * Tests for {@link ContactEditorUtils#shouldShowAccountChangedNotification()}, starting with
     * 0 accounts, and the user selected "local only".
     */
    public void testShouldShowAccountChangedNotification_0Account_localOnly() {
        setAccountTypes(TYPE1);

        // First launch -- always true.
        assertTrue(mTarget.shouldShowAccountChangedNotification());

        // We show the notification here, and user clicked "keep local" and saved an contact.
        mTarget.saveDefaultAccount(AccountWithDataSet.getNullAccount());

        // Now there are no accounts, and default account is null.

        // The user created another contact, but this we shouldn't show the notification.
        assertFalse(mTarget.shouldShowAccountChangedNotification());
    }

    public void testShouldShowAccountChangedNotification_sanity_check() {
        // Prepare 1 account and save it as the default.
        setAccountTypes(TYPE1);
        setAccounts(ACCOUNT_1_A);

        mTarget.saveDefaultAccount(ACCOUNT_1_A);

        // Right after a save, the dialog shouldn't show up.
        assertFalse(mTarget.shouldShowAccountChangedNotification());

        // Remove the default account to emulate broken preferences.
        mTarget.removeDefaultAccountForTest();

        // The dialog shouldn't show up.
        // The logic is, if there's a writable account, we'll pick it as default
        assertFalse(mTarget.shouldShowAccountChangedNotification());
    }

    private static <T> Set<T> toSet(Collection<T> collection) {
        Set<T> ret = Sets.newHashSet();
        ret.addAll(collection);
        return ret;
    }

    private static class MockAccountType extends AccountType {
        private boolean mAreContactsWritable;

        public MockAccountType(String accountType, String dataSet, boolean areContactsWritable) {
            this.accountType = accountType;
            this.dataSet = dataSet;
            mAreContactsWritable = areContactsWritable;
        }

        @Override
        public boolean areContactsWritable() {
            return mAreContactsWritable;
        }

        @Override
        public boolean isGroupMembershipEditable() {
            return true;
        }
    }
}
