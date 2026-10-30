create table cedentes (
    id uuid primary key default uuidv7(),
    nome text not null,
    documento text not null,
    constraint cedentes_documento_unique unique (documento)
);

create table receivables (
    id uuid primary key default uuidv7(),
    cedente_id uuid not null,
    type text not null,
    face_value numeric(19, 2) not null,
    due_date date not null,
    status text not null default 'OPEN',
    version bigint not null default 0,
    created_at timestamptz not null default now(),
    constraint receivables_cedente_fk foreign key (cedente_id) references cedentes (id),
    constraint receivables_type_check check (type in ('DUPLICATA_MERCANTIL', 'CHEQUE')),
    constraint receivables_face_value_positive check (face_value > 0),
    constraint receivables_status_check check (status in ('OPEN', 'SETTLED'))
);

create table base_rates (
    id uuid primary key default uuidv7(),
    rate numeric(12, 8) not null,
    valid_from timestamptz not null,
    created_at timestamptz not null default now()
);

create table fx_rates (
    id uuid primary key default uuidv7(),
    base_currency text not null,
    quote_currency text not null,
    rate numeric(18, 8) not null,
    valid_from timestamptz not null,
    source text not null,
    created_at timestamptz not null default now(),
    constraint fx_rates_rate_positive check (rate > 0)
);

create index fx_rates_pair_valid_from_idx
    on fx_rates (base_currency, quote_currency, valid_from desc);

create table settlements (
    id uuid primary key default uuidv7(),
    receivable_id uuid not null,
    cedente_id uuid not null,
    idempotency_key uuid not null,
    request_hash char(64) not null,
    payment_currency text not null,
    face_value numeric(19, 2) not null,
    term_months numeric(12, 6) not null,
    base_rate numeric(12, 8) not null,
    spread numeric(12, 8) not null,
    present_value_brl numeric(19, 2) not null,
    discount_brl numeric(19, 2) not null,
    fx_rate_id uuid,
    fx_rate numeric(18, 8),
    net_amount numeric(19, 2) not null,
    settled_at timestamptz not null,
    constraint settlements_receivable_unique unique (receivable_id),
    constraint settlements_idempotency_key_unique unique (idempotency_key),
    constraint settlements_receivable_fk foreign key (receivable_id) references receivables (id),
    constraint settlements_cedente_fk foreign key (cedente_id) references cedentes (id),
    constraint settlements_fx_rate_fk foreign key (fx_rate_id) references fx_rates (id),
    constraint settlements_payment_currency_check check (payment_currency in ('BRL', 'USD'))
);

create index settlements_cedente_settled_at_idx
    on settlements (cedente_id, settled_at);

create index settlements_currency_settled_at_idx
    on settlements (payment_currency, settled_at);

create function settlements_reject_mutation()
returns trigger
language plpgsql
as $$
begin
    raise exception 'settlements are immutable';
end;
$$;

create trigger settlements_immutable
    before update or delete on settlements
    for each row
    execute function settlements_reject_mutation();

insert into cedentes (nome, documento) values
    ('Cedente Demonstração 1', 'DOC-DEMO-001'),
    ('Cedente Demonstração 2', 'DOC-DEMO-002'),
    ('Cedente Demonstração 3', 'DOC-DEMO-003');

insert into base_rates (rate, valid_from) values
    (0.01, timestamptz '2026-01-01 00:00:00-03');

insert into fx_rates (base_currency, quote_currency, rate, valid_from, source) values
    ('USD', 'BRL', 5.4321, timestamptz '2026-01-01 00:00:00-03', 'SEED');
