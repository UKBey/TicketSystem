package com.ticketsystem.it_service_backend.scheduler;

import com.ticketsystem.it_service_backend.entity.Ticket;
import com.ticketsystem.it_service_backend.repository.TicketRepository;
import com.ticketsystem.it_service_backend.service.NotificationService;
import com.ticketsystem.it_service_backend.service.SlaPolicyService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.ticketsystem.it_service_backend.entity.Priority;

@ExtendWith(MockitoExtension.class)
class SlaNotificationSchedulerTest {

    @Mock TicketRepository ticketRepository;
    @Mock NotificationService notificationService;
    @Mock SlaPolicyService slaPolicyService;

    @InjectMocks SlaNotificationScheduler scheduler;

    @Nested
    @DisplayName("checkNewlyBreachedTickets()")
    class CheckNewlyBreachedTickets {

        @Test
        @DisplayName("Aşılan bilet yoksa hiçbir işlem yapılmaz")
        void noOverdueTickets_doesNothing() {
            when(ticketRepository.findOverdueUnmarkedTickets(any(), anyList()))
                    .thenReturn(Collections.emptyList());

            scheduler.checkNewlyBreachedTickets();

            verify(ticketRepository, never()).save(any());
            verify(notificationService, never()).notifySlaBreached(any());
        }

        @Test
        @DisplayName("Bilet varsa slaBreached=true set edilir, save ve notify çağrılır")
        void withOverdueTicket_marksBreachedAndNotifies() {
            Ticket ticket = Ticket.builder().id(1L).priority(Priority.HIGH).build();
            when(ticketRepository.findOverdueUnmarkedTickets(any(), anyList()))
                    .thenReturn(List.of(ticket));

            scheduler.checkNewlyBreachedTickets();

            assertThat(ticket.getSlaBreached()).isTrue();
            verify(ticketRepository).save(ticket);
            verify(notificationService).notifySlaBreached(ticket);
        }

        @Test
        @DisplayName("Birden fazla bilet varsa hepsi işlenir")
        void multipleOverdueTickets_processesAll() {
            Ticket t1 = Ticket.builder().id(1L).priority(Priority.CRITICAL).build();
            Ticket t2 = Ticket.builder().id(2L).priority(Priority.HIGH).build();
            when(ticketRepository.findOverdueUnmarkedTickets(any(), anyList()))
                    .thenReturn(List.of(t1, t2));

            scheduler.checkNewlyBreachedTickets();

            verify(ticketRepository, times(2)).save(any());
            verify(notificationService, times(2)).notifySlaBreached(any());
        }
    }

    @Nested
    @DisplayName("checkUpcomingSlaBreaches()")
    class CheckUpcomingSlaBreaches {

        @Test
        @DisplayName("Tüm öncelikler için threshold=0 ise bilet sorgusu yapılmaz")
        void allThresholdsZero_skipsAllPriorities() {
            when(slaPolicyService.getWarningThresholdHours(any())).thenReturn(0);

            scheduler.checkUpcomingSlaBreaches();

            verify(ticketRepository, never()).findPendingWarningTicketsByPriority(any(), any(), any(), any());
            verify(notificationService, never()).notifySlaWarning(any());
        }

        @Test
        @DisplayName("Threshold > 0 ama uyarı listesi boşsa notify çağrılmaz")
        void emptyWarningList_doesNotNotify() {
            when(slaPolicyService.getWarningThresholdHours(any())).thenReturn(2);
            when(ticketRepository.findPendingWarningTicketsByPriority(any(), any(), any(), any()))
                    .thenReturn(Collections.emptyList());

            scheduler.checkUpcomingSlaBreaches();

            verify(notificationService, never()).notifySlaWarning(any());
        }

        @Test
        @DisplayName("Yaklaşan bilet varsa her biri için notifySlaWarning çağrılır")
        void withWarningTickets_notifiesEach() {
            Ticket ticket = Ticket.builder().id(10L).priority(Priority.CRITICAL)
                    .slaDeadline(ZonedDateTime.now().plusHours(1)).build();

            when(slaPolicyService.getWarningThresholdHours("CRITICAL")).thenReturn(2);
            when(slaPolicyService.getWarningThresholdHours("HIGH")).thenReturn(0);
            when(slaPolicyService.getWarningThresholdHours("MEDIUM")).thenReturn(0);
            when(slaPolicyService.getWarningThresholdHours("LOW")).thenReturn(0);
            when(ticketRepository.findPendingWarningTicketsByPriority(
                    anyList(), eq(List.of("CRITICAL")), any(), any()))
                    .thenReturn(List.of(ticket));

            scheduler.checkUpcomingSlaBreaches();

            verify(notificationService).notifySlaWarning(ticket);
        }

        @Test
        @DisplayName("Birden fazla öncelik için bildirim gönderilir")
        void multiplePrioritiesWithTickets_notifiesAll() {
            Ticket critical = Ticket.builder().id(1L).priority(Priority.CRITICAL).build();
            Ticket high = Ticket.builder().id(2L).priority(Priority.HIGH).build();

            when(slaPolicyService.getWarningThresholdHours("CRITICAL")).thenReturn(1);
            when(slaPolicyService.getWarningThresholdHours("HIGH")).thenReturn(2);
            when(slaPolicyService.getWarningThresholdHours("MEDIUM")).thenReturn(0);
            when(slaPolicyService.getWarningThresholdHours("LOW")).thenReturn(0);
            when(ticketRepository.findPendingWarningTicketsByPriority(
                    anyList(), eq(List.of("CRITICAL")), any(), any()))
                    .thenReturn(List.of(critical));
            when(ticketRepository.findPendingWarningTicketsByPriority(
                    anyList(), eq(List.of("HIGH")), any(), any()))
                    .thenReturn(List.of(high));

            scheduler.checkUpcomingSlaBreaches();

            verify(notificationService).notifySlaWarning(critical);
            verify(notificationService).notifySlaWarning(high);
        }
    }
}
