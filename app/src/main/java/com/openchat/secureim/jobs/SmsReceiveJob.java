package com.openchat.secureim.jobs;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.telephony.SmsMessage;
import android.util.Log;

import com.openchat.secureim.crypto.MasterSecret;
import com.openchat.secureim.crypto.MasterSecretUnion;
import com.openchat.secureim.crypto.MasterSecretUtil;
import com.openchat.secureim.database.DatabaseFactory;
import com.openchat.secureim.database.EncryptingSmsDatabase;
import com.openchat.secureim.database.MessagingDatabase.InsertResult;
import com.openchat.secureim.notifications.MessageNotifier;
import com.openchat.secureim.recipients.Recipient;
import com.openchat.secureim.service.KeyCachingService;
import com.openchat.secureim.sms.IncomingTextMessage;
import com.openchat.jobqueue.JobParameters;
import com.openchat.libim.util.guava.Optional;

import java.util.LinkedList;
import java.util.List;

public class SmsReceiveJob extends ContextJob {

  private static final long serialVersionUID = 1L;

  private static final String TAG = SmsReceiveJob.class.getSimpleName();

  private final @Nullable Object[] pdus;
  private final int      subscriptionId;

  public SmsReceiveJob(@NonNull Context context, @Nullable Object[] pdus, int subscriptionId) {
    super(context, JobParameters.newBuilder()
                                .withPersistence()
                                .withWakeLock(true)
                                .create());

    this.pdus           = pdus;
    this.subscriptionId = subscriptionId;
  }

  @Override
  public void onAdded() {}

  @Override
  public void onRun() {
    Log.w(TAG, "onRun()");
    
    Optional<IncomingTextMessage> message      = assembleMessageFragments(pdus, subscriptionId);
    MasterSecret                  masterSecret = KeyCachingService.getMasterSecret(context);

    MasterSecretUnion masterSecretUnion;

    if (masterSecret == null) {
      masterSecretUnion = new MasterSecretUnion(MasterSecretUtil.getAsymmetricMasterSecret(context, null));
    } else {
      masterSecretUnion = new MasterSecretUnion(masterSecret);
    }

    if (message.isPresent() && !isBlocked(message.get())) {
      Optional<InsertResult> insertResult = storeMessage(masterSecretUnion, message.get());

      if (insertResult.isPresent()) {
        MessageNotifier.updateNotification(context, masterSecret, insertResult.get().getThreadId());
      }
    } else if (message.isPresent()) {
      Log.w(TAG, "*** Received blocked SMS, ignoring...");
    } else {
      Log.w(TAG, "*** Failed to assemble message fragments!");
    }
  }

  @Override
  public void onCanceled() {

  }

  @Override
  public boolean onShouldRetry(Exception exception) {
    return false;
  }

  private boolean isBlocked(IncomingTextMessage message) {
    if (message.getSender() != null) {
      Recipient recipient = Recipient.from(context, message.getSender(), false);
      return recipient.isBlocked();
    }

    return false;
  }

  private Optional<InsertResult> storeMessage(MasterSecretUnion masterSecret, IncomingTextMessage message) {
    EncryptingSmsDatabase database = DatabaseFactory.getEncryptingSmsDatabase(context);

    if (message.isSecureMessage()) {
      IncomingTextMessage    placeholder  = new IncomingTextMessage(message, "");
      Optional<InsertResult> insertResult = database.insertMessageInbox(placeholder);
      database.markAsLegacyVersion(insertResult.get().getMessageId());

      return insertResult;
    } else {
      return database.insertMessageInbox(masterSecret, message);
    }
  }

  private Optional<IncomingTextMessage> assembleMessageFragments(@Nullable Object[] pdus, int subscriptionId) {
    if (pdus == null) {
      return Optional.absent();
    }

    List<IncomingTextMessage> messages = new LinkedList<>();

    for (Object pdu : pdus) {
      messages.add(new IncomingTextMessage(context, SmsMessage.createFromPdu((byte[])pdu), subscriptionId));
    }

    if (messages.isEmpty()) {
      return Optional.absent();
    }

    return Optional.of(new IncomingTextMessage(messages));
  }
}
