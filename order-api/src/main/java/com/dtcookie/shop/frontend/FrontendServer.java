package com.dtcookie.shop.frontend;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Statement;
import java.util.concurrent.TimeUnit;

import javax.servlet.http.HttpServletRequest;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.dtcookie.database.Database;
import com.dtcookie.shop.Ports;
import com.dtcookie.shop.Product;
import com.dtcookie.util.Http;
import com.dtcookie.util.Otel;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;

public class FrontendServer {
	
	private static final Logger log = LogManager.getLogger(FrontendServer.class);

	private static final OpenTelemetry openTelemetry = GlobalOpenTelemetry.get();
	private static final Meter meter = openTelemetry.meterBuilder("manual-instrumentation").setInstrumentationVersion("1.0.0").build();
    private static final LongCounter confirmedPurchasesCounter = meter.counterBuilder("shop.purchases.confirmed").setDescription("Number of confirmed purchases").build();
    private static final LongCounter expectedRevenueCounter = meter.counterBuilder("shop.revenue.expected").setDescription("Expected revenue in dollar").build();
	private static final LongCounter actualRevenueCounter = meter.counterBuilder("shop.revenue.actual").setDescription("Actual revenue in dollar").build();
	private static final Tracer tracer = openTelemetry.getTracer("manual-instrumentation");
	private static final LongCounter attemptedPurchasesCounter = meter.counterBuilder("shop.purchases.attempted").setDescription("Attempted number of purchases").build();


	public static void main(String[] args) throws Exception {
		
		Otel.init();
		log.info("Launching Frontend Server on port " + Ports.FRONTEND_LISTEN_PORT);
		Http.serve(
			"order-api-" + System.getenv("GITHUB_USER"),
			Ports.FRONTEND_LISTEN_PORT,
			Http.handler("/place-order", FrontendServer::handlePlaceOrder)
			.add("/purchase-confirmed", FrontendServer::handlePurchaseConfirmed)
		);		
	}

	public static String handlePlaceOrder(HttpServletRequest request) throws Exception {
		// log.info("Placing order");
		Product product = Product.random();
		String productID = product.getID();
		reportExpectedRevenue(product);
		try (Connection con = Database.getConnection(10, TimeUnit.SECONDS)) {
			try (Statement stmt = con.createStatement()) {
				stmt.executeUpdate("INSERT INTO orders VALUES (" + productID + ")");
			}
		}
		validateCreditCard(product);
		return checkInventory(product);
	}

	public static void validateCreditCard(Product product) throws Exception {
		Span span = tracer.spanBuilder("validate-credit-card").setSpanKind(SpanKind.INTERNAL).startSpan();
		try (Scope scope = span.makeCurrent()) {
			Http.JDK.GET("http://order-backend-" + System.getenv("GITHUB_USER") + ":" + Ports.CREDIT_CARD_LISTEN_PORT + "/validate-credit-card/"+product.getID(), null);
			Span childSpan = tracer.spanBuilder("cc-valid").setParent(Context.current().with(span)).startSpan();
			try {
				Thread.sleep(50);
			} finally {
				childSpan.end();
			}			
		} catch (Exception e) {
			span.recordException(e);
			span.setStatus(StatusCode.ERROR);
			throw e;
		} finally {
			span.end();
		}
	}

	public static String checkInventory(Product product) {
		Span span = tracer.spanBuilder("check-inventory").setSpanKind(SpanKind.INTERNAL).startSpan();
		try (Scope scope = span.makeCurrent()) {
			Span childSpan = tracer.spanBuilder("resolve-inventory-backend").setParent(Context.current().with(span)).startSpan();
			try {
				return Http.JDK.GET("http://order-backend-" + System.getenv("GITHUB_USER") + ":" + Ports.INVENTORY_LISTEN_PORT + "/check-inventory/" + URLEncoder.encode(product.getName(), StandardCharsets.UTF_8), null);
			} finally {
				childSpan.end();
			}			
		} catch (Exception e) {
			span.recordException(e);
			span.setStatus(StatusCode.ERROR);
			throw e;
		} finally {
			span.end();
		}		
	}

	public static String handlePurchaseConfirmed(HttpServletRequest request) throws Exception {
		String productID = request.getHeader("product.id");
		Product product = Product.getByID(productID);
		Span span = tracer.spanBuilder("purchase-confirmed").setSpanKind(SpanKind.INTERNAL).startSpan();
		try (Scope scope = span.makeCurrent()) {
			span.setAttribute("product.name", product.getName());
			reportPurchases(product);
			reportActualRevenue(product);
			for (int i = 0; i < 50; i++) {
				Span childSpan = tracer.spanBuilder("persist-purchase-confirmation-" + i).setParent(Context.current().with(span)).startSpan();
				try {
					Thread.sleep(1);
				} finally {
					childSpan.end();
				}
			}
		} catch (Exception e) {
			span.recordException(e);
			span.setStatus(StatusCode.ERROR);
			throw e;
		} finally {
			span.end();
		}		

		return "confirmed";
	}

	private static void reportPurchases(Product product) {
		Attributes attributes = Attributes.builder()
        .put(AttributeKey.stringKey("product"), product.getName())
		.put(AttributeKey.stringKey("user"), System.getenv("GITHUB_USER"))
        .build();
		
		confirmedPurchasesCounter.add(1, attributes);	
	}

	private static void reportExpectedRevenue(Product product) {
		Attributes attributes = Attributes.builder()
        .put(AttributeKey.stringKey("product"), product.getName())
		.put(AttributeKey.stringKey("user"), System.getenv("GITHUB_USER"))
        .build();
		
		expectedRevenueCounter.add(product.getPrice(), attributes);
	}	

private static void reportActualRevenue(Product product) {
    Attributes attributes = Attributes.builder()
    .put(AttributeKey.stringKey("product"), product.getName())
    .put(AttributeKey.stringKey("user"), System.getenv("GITHUB_USER"))
    .build();

    actualRevenueCounter.add(product.getPrice(), attributes);
}
	    private static void reportAttemptedPurchases(Product product) {
        Attributes attributes = Attributes.builder()
        .put(AttributeKey.stringKey("product"), product.getName())
        .put(AttributeKey.stringKey("user"), System.getenv("GITHUB_USER"))
        .build();

        attemptedPurchasesCounter.add(1, attributes);
    }
}
