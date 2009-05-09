-module(orchestrator_feed_sup).

-behaviour(supervisor).

-export([start_link/3]).
-export([init/1]).

-define(SERVER, ?MODULE).

start_link(FeedId, PipelineBroker, EgressBroker) when is_binary(FeedId) ->
    supervisor:start_link({local, list_to_atom("Feed_" ++ binary_to_list(FeedId))},
			  ?MODULE, [FeedId, PipelineBroker, EgressBroker]).

init([FeedId, PipelineBroker, EgressBroker]) ->
    {ok, {{one_for_all, 10, 10},
	  [{orchestrator_plugin_sup, {orchestrator_plugin_sup, start_link, []},
            permanent, 5000, supervisor, [orchestrator_plugin_sup]},
           {orchestrator_feed, {orchestrator_feed, start_link, [FeedId, self(), PipelineBroker, EgressBroker]},
            permanent, 5000, worker, [orchestrator_feed]}]}}.
