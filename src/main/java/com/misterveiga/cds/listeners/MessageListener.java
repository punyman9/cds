/*
 * Author: {Ruben Veiga}
 */
package com.misterveiga.cds.listeners;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.misterveiga.cds.dto.TrelloListDTO;
import com.misterveiga.cds.utils.Properties;
import com.misterveiga.cds.utils.RegexConstants;
import com.misterveiga.cds.utils.RoleUtils;

import kong.unirest.HttpResponse;
import kong.unirest.Unirest;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

/**
 * The listener interface for receiving message events. The class that is
 * interested in processing a message event implements this interface, and the
 * object created with that class is registered with a component using the
 * component's <code>addMessageListener<code> method. When the message event
 * occurs, that object's appropriate method is invoked.
 *
 * @see MessageEvent
 */

@Component
public class MessageListener extends ListenerAdapter {

	/** The log. */
	private final Logger log = LoggerFactory.getLogger(MessageListener.class);

	@Value("${trello.key}")
	public static String trelloKey;

	@Value("${trello.access.token}")
	public static String trelloAccessToken;

	/**
	 * On message received.
	 *
	 * @param event the event
	 */
	@Override
	public void onMessageReceived(final MessageReceivedEvent event) {
		if (event.getAuthor().equals(event.getJDA().getSelfUser())) { // Do nothing if sender is self.
			return;
		}

		if (RoleUtils.findRole(event.getMember(), RoleUtils.ROLE_SERVER_MANAGER) != null) {
			log.debug("Message received from a server manager.");
			scanMessage(event.getMessage(), 4);
		} else if (RoleUtils.findRole(event.getMember(), RoleUtils.ROLE_INTERNAL_AFFAIRS) != null) {
			log.debug("Message received from an internal affairs member.");
			scanMessage(event.getMessage(), 3);
		} else if (RoleUtils.findRole(event.getMember(), RoleUtils.ROLE_SENIOR_COMMUNITY_SUPERVISOR) != null) {
			log.debug("Message received from a senior community supervisor.");
			scanMessage(event.getMessage(), 2);
		} else if (RoleUtils.findRole(event.getMember(), RoleUtils.ROLE_COMMUNITY_SUPERVISOR) != null) {
			log.debug("Message received from a community supervisor.");
			scanMessage(event.getMessage(), 1);
		} else if (RoleUtils.findRole(event.getMember(), RoleUtils.ROLE_TRIAL_SUPERVISOR) != null) {
			log.debug("Message received from a trial supervisor.");
			scanMessage(event.getMessage(), 0);
		} else {
			scanMessage(event.getMessage(), -1);
		}
	}

	private void scanMessage(final Message message, final int i) {

		final String messageText = message.getContentRaw();

		if (!messageText.matches(RegexConstants.GENERIC)) { // If not a command, do nothing.
			return;
		}

		final Member author = message.getMember();
		final String authorMention = author.getAsMention();

		log.info("Command received from authorized user {}: {}", author.getEffectiveName(), messageText);

		switch (i) {
		case 4: // MGMT
//			if (messageText.matches(RegexConstants.COMMAND_SET_COVERAGE_CHECK_TIMER)) {
//				commandSetCoverageTimer(message, messageText, authorMention);
//			} else 
			if (messageText.matches(RegexConstants.COMMAND_HELP)
					|| messageText.matches(RegexConstants.COMMAND_HELP_ALT)) {
				sendHelpMessage(message, authorMention);
			} else {
				sendUnknownCommandMessage(message, authorMention);
			}
			break;
		case 3:
			if (messageText.matches(RegexConstants.COMMAND_IA)) {
				sendHistoryMessage(message, authorMention);
			} else {
				sendUnknownCommandMessage(message, authorMention);
			}
			break;
		case 2: // SCS
		case 1: // CS
		case 0: // TS
			break;
		case -1: // Member with no command roles.
			break;
		}

	}

	private void sendHelpMessage(final Message message, final String authorMention) {
		message.getChannel()
				.sendMessage(new StringBuilder().append(authorMention).append(" **Roblox Discord Services | Help**")
						.append("\nPrefix for all commands: `rdss:<command>`").append("\nNothing to see here..."))
				.queue();
	}

	// TODO: As this invokes Trello API I would separate into its own class, such as
	// Telegram implementation in com.misterveiga.cds.telegram.
	private void sendHistoryMessage(final Message message, final String authorMention) {
		try {
			final HttpResponse<String> boardlists = Unirest
					.get("https://api.trello.com/1/boards/5ed7949d405d7d6fd00c201c/lists").queryString("key", trelloKey)
					.queryString("token", trelloAccessToken).asString();

			List<TrelloListDTO> trelloLists;

			trelloLists = Arrays.asList(new ObjectMapper().readValue(boardlists.getBody(), TrelloListDTO[].class));

			for (final TrelloListDTO list : trelloLists) {
				log.info(list.toString());
			}

		} catch (final JsonParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (final JsonMappingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (final IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	private void sendUnknownCommandMessage(final Message message, final String authorMention) {
		message.getChannel()
				.sendMessage(new StringBuilder().append(authorMention)
						.append(" Sorry, I don't know that command.\n*Use rdss:? or rdss:help for assistance.*"))
				.queue();
	}

	private void commandSetCoverageTimer(final Message message, final String messageText, final String authorMention) {
		final String[] contents = messageText.split(" ");
		try {
			final Integer newDuration = Integer.parseInt(contents[1]);
			if (newDuration > 0) {
				Properties.setTimeWaitOnlineSupervisorMonitoring(TimeUnit.MINUTES.toMillis(newDuration));
				message.getChannel()
						.sendMessage(new StringBuilder().append(authorMention)
								.append(" Supervisor Monitoring check interval set to ").append(newDuration)
								.append(" minutes. Effective on next check."))
						.queue();
			} else {
				message.getChannel().sendMessage(new StringBuilder().append(authorMention).append(
						" Supervisor Monitoring check interval must be at least 1 minute.\n[SYNTAX: `rdss:set_coverage_timer 1-9999`"))
						.queue();
			}
		} catch (final NumberFormatException e) {
			message.getChannel().sendMessage(new StringBuilder().append(authorMention)
					.append(" Incorrect value. SYNTAX: `rdss:set_coverage_timer 1-9999`")).queue();
		}
	}

}